#!/usr/bin/env bash

set -euo pipefail

root_dir="${1:-.}"

ruby - "${root_dir}" <<'RUBY'
require "rexml/document"

Platform = Struct.new(:name, :artifact_id, :property_name)

PLATFORMS = [
  Platform.new("Spring Boot-only", "jfoundry-spring-boot-dependencies", "spring-boot.version"),
  Platform.new("Spring Cloud", "jfoundry-spring-cloud-dependencies", "spring-cloud.version"),
  Platform.new("Spring Cloud Alibaba", "jfoundry-spring-cloud-dependencies", "spring-cloud-alibaba.version"),
  Platform.new("Quarkus", "jfoundry-quarkus-dependencies", "quarkus.version"),
  Platform.new("Helidon MP", "jfoundry-helidon-dependencies", "helidon.version")
].freeze

def fail_matrix(message)
  warn "Compatibility matrix is invalid: #{message}"
  exit 1
end

def child(element, name)
  element&.elements&.find { |candidate| candidate.name == name }
end

def property_value(path, property_name)
  begin
    project = REXML::Document.new(File.read(path)).root
  rescue Errno::ENOENT
    fail_matrix("missing runtime BOM: #{path}")
  rescue REXML::ParseException => error
    fail_matrix("#{path} is not valid XML: #{error.message.lines.first.strip}")
  end

  value = child(child(project, "properties"), property_name)&.text.to_s.strip
  fail_matrix("#{path} must define #{property_name}") if value.empty?
  value
end

def version_line(version)
  match = /\A(\d+)\.(\d+)(?:\.|\z)/.match(version)
  fail_matrix("cannot derive a supported line from version #{version}") unless match
  "#{match[1]}.#{match[2]}.x"
end

root_dir = File.expand_path(ARGV.fetch(0))
matrix_path = File.join(root_dir, "docs", "release", "compatibility.md")
fail_matrix("missing document: #{matrix_path}") unless File.file?(matrix_path)

rows = {}
File.foreach(matrix_path) do |line|
  next unless line.start_with?("|")

  columns = line.split("|", -1)[1...-1].map(&:strip)
  next unless columns.size >= 4
  next unless PLATFORMS.any? { |platform| platform.name == columns[0] }

  fail_matrix("duplicate platform row: #{columns[0]}") if rows.key?(columns[0])
  rows[columns[0]] = columns
end

PLATFORMS.each do |platform|
  row = rows[platform.name]
  fail_matrix("missing platform row: #{platform.name}") unless row

  supported_line = row[1]
  documented_version = row[2]
  documented_source = row[3].delete("`")
  pom_path = File.join(root_dir, "jfoundry-boms", platform.artifact_id, "pom.xml")
  bom_version = property_value(pom_path, platform.property_name)
  expected_line = version_line(bom_version)

  if documented_version != bom_version
    fail_matrix(
      "#{platform.name} verified version #{documented_version} must match " \
      "#{platform.artifact_id} #{platform.property_name} #{bom_version}"
    )
  end
  if supported_line != expected_line
    fail_matrix(
      "#{platform.name} supported line #{supported_line} must match verified version line #{expected_line}"
    )
  end
  if documented_source != platform.artifact_id
    fail_matrix(
      "#{platform.name} exact version source #{documented_source} must be #{platform.artifact_id}"
    )
  end
end

puts "Compatibility matrix verification passed."
RUBY
