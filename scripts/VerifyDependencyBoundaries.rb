require "find"
require "pathname"
require "rexml/document"
require "yaml"

ROOT = Pathname(ARGV.fetch(0, ".")).expand_path
EXCLUDED_DIRECTORIES = %w[.git .worktrees target].freeze

def classify(relative_path)
  path = relative_path.to_s.tr("\\", "/")
  return :core if path.start_with?("jfoundry-core/")
  return :spring_bom if path.start_with?("jfoundry-boms/jfoundry-spring-") && path.end_with?("-dependencies")
  return :quarkus_bom if path.start_with?("jfoundry-boms/jfoundry-quarkus-dependencies")
  return :helidon_bom if path.start_with?("jfoundry-boms/jfoundry-helidon-dependencies")
  return :spring if path.start_with?("jfoundry-runtime/jfoundry-spring/")
  return :jakarta if path.start_with?("jfoundry-runtime/jfoundry-jakarta/")
  return :quarkus if path.start_with?("jfoundry-runtime/jfoundry-quarkus/")
  return :helidon if path.start_with?("jfoundry-runtime/jfoundry-helidon/")
  return :foundation_bom if path.start_with?("jfoundry-boms/jfoundry-foundation-dependencies")

  :other
end

def spring?(coordinate)
  coordinate.start_with?("org.springframework:", "org.springframework.") ||
    coordinate.include?(":spring-") || coordinate.include?("-spring-") ||
    coordinate.include?(":jmolecules-spring") ||
    (coordinate.include?(":jfoundry-") && coordinate.include?("-spring"))
end

def quarkus?(coordinate)
  coordinate.start_with?("io.quarkus:", "io.quarkus.") ||
    (coordinate.include?(":jfoundry-") && coordinate.include?("-quarkus"))
end

def helidon?(coordinate)
  coordinate.start_with?("io.helidon:", "io.helidon.") ||
    (coordinate.include?(":jfoundry-") && coordinate.include?("-helidon"))
end

def runtime_bom?(module_kind)
  %i[spring_bom quarkus_bom helidon_bom].include?(module_kind)
end

def disallowed_runtime_bom_import?(coordinate)
  %w[
    io.github.xfoundries:jfoundry-dependencies
    io.github.xfoundries:jfoundry-foundation-dependencies
    io.github.xfoundries:jfoundry-spring-boot-dependencies
    io.github.xfoundries:jfoundry-spring-cloud-dependencies
    io.github.xfoundries:jfoundry-quarkus-dependencies
    io.github.xfoundries:jfoundry-helidon-dependencies
  ].include?(coordinate)
end

def check_dependency(module_kind, dependency, context, pom, violations)
  group_id = dependency.fetch("groupId", "").to_s
  artifact_id = dependency.fetch("artifactId", "").to_s
  return if group_id.empty? || artifact_id.empty?

  coordinate = "#{group_id}:#{artifact_id}".downcase
  scope = dependency.fetch("scope", "").to_s
  location = scope.empty? ? context : "#{context}:#{scope}"
  spring = spring?(coordinate)
  quarkus = quarkus?(coordinate)
  helidon = helidon?(coordinate)
  rules = []
  if module_kind == :core && (spring || quarkus || helidon) &&
      coordinate != "jakarta.persistence:jakarta.persistence-api"
    rules << "core-runtime-dependency"
  end
  if module_kind == :foundation_bom &&
      (spring || quarkus || helidon || coordinate.include?("-deployment") || coordinate.include?("-starter"))
    rules << "foundation-runtime-coordinate"
  end
  rules << "jakarta-cross-runtime-dependency" if module_kind == :jakarta && (spring || quarkus || helidon)
  rules << "spring-cross-runtime-dependency" if %i[spring spring_bom].include?(module_kind) && (quarkus || helidon)
  rules << "quarkus-cross-runtime-dependency" if %i[quarkus quarkus_bom].include?(module_kind) && (spring || helidon)
  rules << "helidon-cross-runtime-dependency" if %i[helidon helidon_bom].include?(module_kind) && (spring || quarkus)
  if runtime_bom?(module_kind) && disallowed_runtime_bom_import?(coordinate)
    rules << "runtime-bom-import"
  end
  rules.each { |rule| violations << "#{pom} [#{rule}] #{coordinate} (#{location})" }
end

def yaml_dependency_sets(model)
  sets = []
  dependencies = model["dependencies"]
  sets << [dependencies, "dependencies"] if dependencies.is_a?(Array)
  managed = model.dig("dependencyManagement", "dependencies")
  sets << [managed, "dependencyManagement"] if managed.is_a?(Array)
  Array(model["profiles"]).each do |profile|
    sets.concat(yaml_dependency_sets(profile)) if profile.is_a?(Hash)
  end
  sets
end

def xml_dependency_sets(document)
  sets = []
  REXML::XPath.each(document, "//*[local-name()='dependencies']") do |dependencies|
    next if dependencies.parent&.name == "plugin"

    context = REXML::XPath.first(dependencies, "ancestor::*[local-name()='dependencyManagement']") ?
      "dependencyManagement" : "dependencies"
    values = dependencies.get_elements("*[local-name()='dependency']").map do |dependency|
      %w[groupId artifactId scope].to_h do |name|
        element = dependency.elements["*[local-name()='#{name}']"]
        [name, element&.text.to_s.strip]
      end
    end
    sets << [values, context]
  end
  sets
end

def load_dependency_sets(pom)
  if pom.extname == ".yaml"
    model = YAML.safe_load(pom.read, permitted_classes: [], permitted_symbols: [], aliases: true)
    raise "top-level model must be a mapping" unless model.is_a?(Hash)

    yaml_dependency_sets(model)
  else
    xml_dependency_sets(REXML::Document.new(pom.read))
  end
end

violations = []
Find.find(ROOT.to_s) do |entry|
  path = Pathname(entry)
  if path.directory? && path != ROOT && EXCLUDED_DIRECTORIES.include?(path.basename.to_s)
    Find.prune
  end
  next unless path.file? && %w[pom.yaml pom.xml].include?(path.basename.to_s)

  relative_pom = path.relative_path_from(ROOT)
  module_kind = classify(relative_pom.dirname)
  begin
    load_dependency_sets(path).each do |dependencies, context|
      dependencies.each do |dependency|
        check_dependency(module_kind, dependency, context, relative_pom, violations)
      end
    end
  rescue StandardError => error
    violations << "#{relative_pom} [parse-error] #{error.message}"
  end
end

unless violations.empty?
  violations.each { |violation| warn violation }
  warn "Dependency boundary check failed with #{violations.size} violation(s)."
  exit 1
end
