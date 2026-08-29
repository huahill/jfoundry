require "pathname"
require "rexml/document"

repository_root = Pathname(ARGV.fetch(0)).expand_path
new_version = ARGV.fetch(1)
unless new_version.match?(/\A[0-9A-Za-z][0-9A-Za-z.+_-]*\z/)
  warn "Invalid Maven project version: #{new_version}"
  exit 2
end

root_pom = repository_root.join("pom.xml")
unless root_pom.file?
  warn "Maven reactor root does not exist: #{root_pom}"
  exit 1
end

root_document = REXML::Document.new(root_pom.read)
root_project = root_document.root
old_version = root_project.elements["version"]&.text.to_s.strip
if old_version.empty?
  warn "Maven reactor root has no project version: #{root_pom}"
  exit 1
end
if old_version == new_version
  warn "New version matches the current reactor version: #{new_version}"
  exit 2
end
root_group_id = root_project.elements["groupId"]&.text.to_s.strip

excluded_directories = %w[.git .worktrees graphify-out target]
pom_files = Dir.glob(repository_root.join("**/pom.xml")).map { |path| Pathname(path) }.reject do |pom|
  pom.relative_path_from(repository_root).each_filename.any? { |part| excluded_directories.include?(part) || part == "test" }
end.sort
edits = {}
updated_references = 0

pom_files.each do |pom|
  document = REXML::Document.new(pom.read)
  project = document.root
  targets = 0
  targets += 1 if project.elements["version"]&.text.to_s.strip == old_version
  parent = project.elements["parent"]
  targets += 1 if parent && parent.elements["groupId"]&.text.to_s.strip == root_group_id && parent.elements["version"]&.text.to_s.strip == old_version
  targets += 1 if project.elements["properties/jfoundry.version"]&.text.to_s.strip == old_version

  content = pom.read
  occurrences = content.scan(/<(?:version|jfoundry\.version)>#{Regexp.escape(old_version)}<\/(?:version|jfoundry\.version)>/).length
  unless occurrences == targets
    next if targets.zero? && occurrences.zero?

    warn "#{pom.relative_path_from(repository_root)}: unclassified #{old_version} occurrence(s); expected #{targets}, found #{occurrences}"
    exit 1
  end
  next if targets.zero?

  edits[pom] = content.gsub(/(<(?:version|jfoundry\.version)>)#{Regexp.escape(old_version)}(<\/(?:version|jfoundry\.version)>)/, "\\1#{new_version}\\2")
  updated_references += targets
end

if edits.empty?
  warn "No reactor version references matched #{old_version}"
  exit 1
end

edits.each { |pom, content| pom.write(content) }
puts "Updated #{updated_references} reactor version references across #{edits.size} XML POMs: #{old_version} -> #{new_version}"
