#!/usr/bin/env bash

set -euo pipefail

pom_file="${1:-pom.yaml}"

if [[ ! -f "${pom_file}" ]]; then
    echo "Maven POM does not exist: ${pom_file}" >&2
    exit 1
fi

ruby - "${pom_file}" <<'RUBY'
require "rexml/document"
require "yaml"

path = ARGV.fetch(0)

if File.extname(path) == ".yaml"
  project = YAML.safe_load(File.read(path), aliases: true)
  release = Array(project["profiles"]).find { |profile| profile["id"] == "release" }
  plugin = Array(release&.dig("build", "plugins")).find do |candidate|
    candidate["groupId"] == "org.cyclonedx" &&
      candidate["artifactId"] == "cyclonedx-maven-plugin"
  end
  execution = Array(plugin&.fetch("executions", nil)).find do |candidate|
    candidate["phase"] == "package" && Array(candidate["goals"]).include?("makeAggregateBom")
  end
  configuration = plugin&.fetch("configuration", nil)
  valid = execution && configuration&.fetch("outputFormat", nil) == "all" &&
    configuration&.fetch("includeTestScope", nil) == false
else
  project = REXML::Document.new(File.read(path)).root
  release = project.elements.to_a("profiles/profile").find do |profile|
    profile.elements["id"]&.text.to_s.strip == "release"
  end
  plugin = release&.elements&.to_a("build/plugins/plugin")&.find do |candidate|
    candidate.elements["groupId"]&.text.to_s.strip == "org.cyclonedx" &&
      candidate.elements["artifactId"]&.text.to_s.strip == "cyclonedx-maven-plugin"
  end
  execution = plugin&.elements&.to_a("executions/execution")&.find do |candidate|
    candidate.elements["phase"]&.text.to_s.strip == "package" &&
      candidate.elements.to_a("goals/goal").any? { |goal| goal.text.to_s.strip == "makeAggregateBom" }
  end
  configuration = plugin&.elements&.[]("configuration")
  valid = execution && configuration&.elements&.[]("outputFormat")&.text.to_s.strip == "all" &&
    configuration&.elements&.[]("includeTestScope")&.text.to_s.strip == "false"
end

unless valid
  warn "Release SBOM configuration is incomplete: #{path}"
  exit 1
end
RUBY

echo "Release SBOM verification passed: ${pom_file}"
