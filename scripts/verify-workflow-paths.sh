#!/usr/bin/env bash

set -euo pipefail

root_dir="${1:-.}"

require_text() {
    local file="$1"
    local text="$2"
    if ! grep -Fq -- "${text}" "${root_dir}/${file}"; then
        echo "${file} must contain: ${text}" >&2
        exit 1
    fi
}

for workflow in .github/workflows/codeql.yml .github/workflows/snapshot.yml; do
    if [[ ! -f "${root_dir}/${workflow}" ]]; then
        echo "Workflow does not exist: ${workflow}" >&2
        exit 1
    fi
done

require_text ".github/workflows/ci.yml" "README.md|README_ZH.md|AGENTS.md|docs/*)"

ruby - "${root_dir}/.github/workflows/codeql.yml" "${root_dir}/.github/workflows/snapshot.yml" <<'RUBY'
require "yaml"

expected = ["README.md", "README_ZH.md", "AGENTS.md", "docs/**"]

ARGV.each do |path|
  source = File.read(path)
  workflow = YAML.safe_load(source, aliases: false)
  events = workflow[true] || workflow["on"]
  push = events.fetch("push")
  actual = push.fetch("paths-ignore")
  abort "#{path} push paths-ignore must be #{expected.inspect}" unless actual == expected

  if path.end_with?("codeql.yml")
    pull_request = events.fetch("pull_request")
    actual_pull_request = pull_request.fetch("paths-ignore")
    abort "#{path} pull_request paths-ignore must be #{expected.inspect}" unless actual_pull_request == expected
  end
end
RUBY

echo "Workflow path verification passed: documentation whitelist is README.md, README_ZH.md, AGENTS.md, docs/**"
