#!/usr/bin/env bash

set -euo pipefail

root_dir="${1:-.}"

require_file() {
    local file="$1"
    if [[ ! -f "${root_dir}/${file}" ]]; then
        echo "Supply-chain configuration is missing: ${file}" >&2
        exit 1
    fi
}

require_text() {
    local file="$1"
    local text="$2"
    if ! grep -Fq -- "${text}" "${root_dir}/${file}"; then
        echo "${file} must contain: ${text}" >&2
        exit 1
    fi
}

forbid_text() {
    local file="$1"
    local text="$2"
    if grep -Fq -- "${text}" "${root_dir}/${file}"; then
        echo "${file} must not contain: ${text}" >&2
        exit 1
    fi
}

verify_dependabot_policy() {
    ruby - "${root_dir}/.github/dependabot.yml" <<'RUBY'
require "yaml"

def fail_policy(message)
    warn "Dependabot update policy is invalid: #{message}"
    exit 1
end

path = ARGV.fetch(0)
begin
    config = YAML.safe_load(File.read(path), aliases: false)
rescue Psych::Exception
    fail_policy("could not safely parse YAML")
end
fail_policy("root must be a mapping") unless config.is_a?(Hash)

updates = config["updates"]
fail_policy("updates must be an array") unless updates.is_a?(Array)
updates.each do |update|
    fail_policy("each updates entry must be a mapping") unless update.is_a?(Hash)
end

maven_updates = updates.select { |update| update["package-ecosystem"] == "maven" }
fail_policy("must contain exactly one Maven updates entry") unless maven_updates.size == 1

expected_maven_groups = {
    "jfoundry-maven-patches" => {
        "patterns" => ["*"],
        "update-types" => ["patch"]
    }
}
maven_update = maven_updates.first
fail_policy("Maven groups must be #{expected_maven_groups.inspect}") unless maven_update["groups"] == expected_maven_groups

expected_ignore_names = [
    "org.springframework.boot:spring-boot-dependencies",
    "org.springframework.boot:spring-boot-starter-parent",
    "org.springframework.boot:spring-boot-maven-plugin",
    "org.springframework.cloud:spring-cloud-dependencies",
    "com.alibaba.cloud:spring-cloud-alibaba-dependencies"
]
ignores = maven_update["ignore"]
fail_policy("Maven ignores must be an array") unless ignores.is_a?(Array)
ignore_names = ignores.map do |ignore|
    fail_policy("each Maven ignore must contain only dependency-name") unless ignore.is_a?(Hash) && ignore.keys == ["dependency-name"]
    ignore["dependency-name"]
end
unless ignore_names.size == expected_ignore_names.size && ignore_names.sort == expected_ignore_names.sort
    fail_policy("Maven ignore dependency names must be exactly #{expected_ignore_names.inspect}")
end

github_actions_updates = updates.select { |update| update["package-ecosystem"] == "github-actions" }
fail_policy("must contain exactly one GitHub Actions updates entry") unless github_actions_updates.size == 1

expected_github_actions_groups = {
    "github-codeql-action" => {
        "patterns" => ["github/codeql-action/*"]
    }
}
unless github_actions_updates.first["groups"] == expected_github_actions_groups
    fail_policy("GitHub Actions groups must be #{expected_github_actions_groups.inspect}")
end
RUBY
}

require_file ".github/dependabot.yml"
require_file ".github/workflows/codeql.yml"
require_file ".github/workflows/release.yml"
require_file ".github/workflows/ci.yml"
require_file ".github/workflows/snapshot.yml"
require_file ".github/workflows/auto-merge-dependabot.yml"

require_text ".github/dependabot.yml" "package-ecosystem: maven"
require_text ".github/dependabot.yml" "package-ecosystem: github-actions"
verify_dependabot_policy
require_text ".github/workflows/codeql.yml" "security-events: write"
require_text ".github/workflows/codeql.yml" "github/codeql-action/init"
require_text ".github/workflows/codeql.yml" "github/codeql-action/analyze"
require_text ".github/workflows/codeql.yml" "language: java-kotlin"
require_text ".github/workflows/codeql.yml" "build-mode: manual"
require_text ".github/workflows/codeql.yml" "language: actions"
require_text ".github/workflows/codeql.yml" "build-mode: none"
require_text ".github/workflows/codeql.yml" 'build-mode: ${{ matrix.build-mode }}'
require_text ".github/workflows/codeql.yml" "actions/setup-java"
require_text ".github/workflows/codeql.yml" "java-version: 25"
require_text ".github/workflows/codeql.yml" "-pl '!jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests'"
require_text ".github/workflows/ci.yml" "name: Dependency Review"
require_text ".github/workflows/ci.yml" "actions/dependency-review-action"
require_text ".github/workflows/ci.yml" "fail-on-severity: high"
require_text ".github/workflows/ci.yml" "needs.dependency-review.result"
require_text ".github/workflows/release.yml" "actions/upload-artifact"
require_text ".github/workflows/release.yml" "release-evidence"
require_text ".github/workflows/snapshot.yml" "sed -n 's/^\\[INFO\\] \\[stdout\\] //p'"
require_text ".github/workflows/snapshot.yml" "is_snapshot=true"
require_text ".github/workflows/snapshot.yml" "if: steps.version.outputs.is_snapshot == 'true'"
require_text ".github/workflows/auto-merge-dependabot.yml" "pull_request_target:"
require_text ".github/workflows/auto-merge-dependabot.yml" "dependabot[bot]"
require_text ".github/workflows/auto-merge-dependabot.yml" "pull-requests: write"
require_text ".github/workflows/auto-merge-dependabot.yml" 'gh pr merge "${PR_NUMBER}" --auto --rebase'
if grep -Fq -- "-DforceStdout | tail -n 1" "${root_dir}/.github/workflows/snapshot.yml"; then
    echo ".github/workflows/snapshot.yml must not use bare Maven 4 version extraction" >&2
    exit 1
fi

echo "Supply-chain workflow verification passed: ${root_dir}"
