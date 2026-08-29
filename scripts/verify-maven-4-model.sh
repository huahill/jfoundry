#!/usr/bin/env bash

set -euo pipefail

repository_root="${1:-.}"
expected_count="${2:-122}"

if [[ ! -d "${repository_root}" ]]; then
    echo "Maven 4 model verification root does not exist: ${repository_root}" >&2
    exit 1
fi

REPOSITORY_ROOT="${repository_root}" EXPECTED_COUNT="${expected_count}" ruby <<'RUBY'
require "rexml/document"

root = File.expand_path(ENV.fetch("REPOSITORY_ROOT"))
expected_count = Integer(ENV.fetch("EXPECTED_COUNT"))

fail_verification = lambda do |message|
  warn "Maven 4.1 model verification failed: #{message}"
  exit 1
end

yaml_files = Dir.glob(File.join(root, "**", "pom.yaml"), File::FNM_DOTMATCH)
fail_verification.call("source tree contains pom.yaml: #{yaml_files.first}") unless yaml_files.empty?

pom_files = Dir.glob(File.join(root, "**", "pom.xml"), File::FNM_DOTMATCH).reject do |path|
  path.split(File::SEPARATOR).include?("target") || path.include?(File.join("src", "test"))
end.sort
fail_verification.call("expected #{expected_count} source POMs, found #{pom_files.size}") unless pom_files.size == expected_count

aggregator_count = 0
pom_files.each do |path|
  relative_path = path.delete_prefix("#{root}#{File::SEPARATOR}")
  begin
    document = REXML::Document.new(File.read(path))
  rescue REXML::ParseException => error
    fail_verification.call("#{relative_path} is not well-formed XML: #{error.message}")
  end

  project = document.root
  fail_verification.call("#{relative_path} must have a project root element") unless project&.name == "project"
  fail_verification.call("#{relative_path} must use the Maven 4.1.0 namespace") unless project.namespace == "http://maven.apache.org/POM/4.1.0"

  model_version = project.elements["modelVersion"]&.text.to_s
  fail_verification.call("#{relative_path} must declare modelVersion 4.1.0") unless model_version == "4.1.0"

  if expected_count == 122
    schema_location = project.attributes["xsi:schemaLocation"].to_s.split.join(" ")
    expected_schema_location = "http://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd"
    fail_verification.call("#{relative_path} must reference the Maven 4.1.0 XSD") unless schema_location == expected_schema_location
  end

  modules = project.get_elements(".//modules")
  fail_verification.call("#{relative_path} must use subprojects instead of modules") unless modules.empty?

  subprojects = project.get_elements(".//subprojects")
  next if subprojects.empty?

  aggregator_count += 1
  subprojects.each do |container|
    container.elements.each do |child|
      fail_verification.call("#{relative_path} subprojects may contain only subproject elements") unless child.name == "subproject"
      fail_verification.call("#{relative_path} has an empty subproject path") if child.text.to_s.strip.empty?
    end
  end
end

fail_verification.call("expected 8 subprojects aggregators, found #{aggregator_count}") unless aggregator_count == 8 || expected_count != 122
puts "Maven 4.1 model verification passed: #{pom_files.size} source POMs, #{aggregator_count} subprojects aggregators."
RUBY
