require "pathname"
require "psych"
require "yaml"

repository_root = Pathname(ARGV.fetch(0)).expand_path
new_version = ARGV.fetch(1)
unless new_version.match?(/\A[0-9A-Za-z][0-9A-Za-z.+_-]*\z/)
  warn "Invalid Maven project version: #{new_version}"
  exit 2
end

def mapping_value(mapping, key)
  return unless mapping.is_a?(Psych::Nodes::Mapping)

  mapping.children.each_slice(2) do |candidate_key, value|
    return value if candidate_key.is_a?(Psych::Nodes::Scalar) && candidate_key.value == key
  end
  nil
end

def scalar_value(mapping, key)
  node = mapping_value(mapping, key)
  node.value if node.is_a?(Psych::Nodes::Scalar)
end

def scalar_nodes(node, value, result = [])
  result << node if node.is_a?(Psych::Nodes::Scalar) && node.value == value
  Array(node.children).each { |child| scalar_nodes(child, value, result) } if node.respond_to?(:children)
  result
end

def rendered_scalar(node, value)
  case node.style
  when Psych::Nodes::Scalar::SINGLE_QUOTED
    "'#{value.gsub("'", "''")}'"
  when Psych::Nodes::Scalar::DOUBLE_QUOTED
    value.dump
  else
    value
  end
end

root_pom = repository_root.join("pom.yaml")
unless root_pom.file?
  warn "Mason reactor root does not exist: #{root_pom}"
  exit 1
end

root_model = YAML.safe_load(root_pom.read, permitted_classes: [], permitted_symbols: [], aliases: true)
old_version = root_model.fetch("version").to_s
if old_version == new_version
  warn "New version matches the current reactor version: #{new_version}"
  exit 2
end
root_group_id = root_model.fetch("groupId").to_s

excluded_directories = %w[.git .worktrees graphify-out target]
pom_files = Dir.glob(repository_root.join("**/pom.yaml")).map { |path| Pathname(path) }.reject do |pom|
  pom.relative_path_from(repository_root).each_filename.any? { |part| excluded_directories.include?(part) }
end.sort
edits = {}
updated_references = 0

pom_files.each do |pom|
  stream = Psych.parse_stream(pom.read, filename: pom.to_s)
  document = stream.children.fetch(0)
  model = document.root
  raise "#{pom}: top-level model must be a mapping" unless model.is_a?(Psych::Nodes::Mapping)

  targets = []
  project_version = mapping_value(model, "version")
  targets << project_version if project_version.is_a?(Psych::Nodes::Scalar) && project_version.value == old_version

  parent = mapping_value(model, "parent")
  if parent.is_a?(Psych::Nodes::Mapping) && scalar_value(parent, "groupId") == root_group_id
    parent_version = mapping_value(parent, "version")
    targets << parent_version if parent_version.is_a?(Psych::Nodes::Scalar) && parent_version.value == old_version
  end

  properties = mapping_value(model, "properties")
  if properties.is_a?(Psych::Nodes::Mapping)
    framework_version = mapping_value(properties, "jfoundry.version")
    targets << framework_version if framework_version.is_a?(Psych::Nodes::Scalar) && framework_version.value == old_version
  end

  unsupported = scalar_nodes(model, old_version) - targets
  unless unsupported.empty?
    lines = unsupported.map { |node| node.start_line + 1 }.join(", ")
    raise "#{pom}: unclassified #{old_version} occurrence at line(s) #{lines}"
  end
  next if targets.empty?

  lines = pom.readlines
  targets.sort_by { |node| [node.start_line, node.start_column] }.reverse_each do |node|
    line = lines.fetch(node.start_line)
    lines[node.start_line] = line[0...node.start_column] +
      rendered_scalar(node, new_version) + line[node.end_column..]
  end
  edits[pom] = lines.join
  updated_references += targets.size
end

raise "No reactor version references matched #{old_version}" if edits.empty?

edits.each { |pom, content| pom.write(content) }
puts "Updated #{updated_references} reactor version references across #{edits.size} Mason YAML POMs: #{old_version} -> #{new_version}"
