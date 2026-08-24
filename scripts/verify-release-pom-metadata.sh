#!/usr/bin/env bash

set -euo pipefail

root_dir="${1:-.}"

ruby - "${root_dir}" <<'RUBY'
require "rexml/document"

def fail_metadata(message)
  warn "Release POM metadata is invalid: #{message}"
  exit 1
end

def child(element, name)
  element&.elements&.find { |candidate| candidate.name == name }
end

def child_text(element, name)
  child(element, name)&.text.to_s.strip
end

root_dir = File.expand_path(ARGV.fetch(0))
pom_paths = [File.join(root_dir, "pom.xml"), *Dir[File.join(root_dir, "jfoundry-boms", "*", "pom.xml")].sort]
fail_metadata("root pom.xml does not exist") unless File.file?(pom_paths.first)
fail_metadata("no independent BOM or parent POMs were found under jfoundry-boms") if pom_paths.size == 1

records = pom_paths.map do |path|
  begin
    project = REXML::Document.new(File.read(path)).root
  rescue REXML::ParseException => error
    fail_metadata("#{path.delete_prefix("#{root_dir}/")} is not valid XML: #{error.message.lines.first.strip}")
  end

  relative_path = path.delete_prefix("#{root_dir}/")
  version = child_text(project, "version")
  scm_tag = child_text(child(project, "scm"), "tag")
  fail_metadata("#{relative_path} must declare a direct project version") if version.empty?
  fail_metadata("#{relative_path} must declare a direct scm/tag") if scm_tag.empty?
  [relative_path, version, scm_tag]
end

reactor_version = records.first.fetch(1)
records.each do |path, version, _scm_tag|
  next if version == reactor_version

  fail_metadata("#{path} version #{version} must match reactor version #{reactor_version}")
end

if reactor_version.end_with?("-SNAPSHOT")
  base_version = reactor_version.delete_suffix("-SNAPSHOT")
  match = /\A(\d+)\.(\d+)\.0\z/.match(base_version)
  fail_metadata("unsupported SNAPSHOT development version #{reactor_version}") unless match

  major = match[1].to_i
  minor = match[2].to_i
  fail_metadata("SNAPSHOT development version must follow a stable minor release: #{reactor_version}") if minor.zero?
  expected_literal_tag = "v#{major}.#{minor - 1}.0"
else
  expected_literal_tag = "v#{reactor_version}"
end

dynamic_tag = 'v${project.version}'
records.each do |path, _version, scm_tag|
  next if scm_tag == dynamic_tag || scm_tag == expected_literal_tag

  fail_metadata("#{path} scm/tag #{scm_tag} must be #{dynamic_tag} or #{expected_literal_tag}")
end

puts "Release POM metadata verification passed: #{reactor_version}"
RUBY
