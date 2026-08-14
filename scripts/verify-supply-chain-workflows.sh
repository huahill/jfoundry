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

verify_dependabot_auto_merge_workflow() {
    ruby - "${root_dir}/.github/workflows/auto-merge-dependabot.yml" <<'RUBY'
require "yaml"

def fail_workflow(message)
    warn "Dependabot auto-merge workflow is invalid: #{message}"
    exit 1
end

def workflow_values(value)
    case value
    when Hash
        value.values.flat_map { |child| workflow_values(child) }
    when Array
        value.flat_map { |child| workflow_values(child) }
    else
        [value.to_s]
    end
end

path = ARGV.fetch(0)
source = File.read(path)
begin
    workflow = YAML.safe_load(source, aliases: false)
rescue Psych::Exception
    fail_workflow("could not safely parse YAML")
end
fail_workflow("root must be a mapping") unless workflow.is_a?(Hash)

events = workflow[true] || workflow["on"]
fail_workflow("must run only on pull_request_target") unless events.is_a?(Hash) && events.keys == ["pull_request_target"]

expected_permissions = { "contents" => "write", "pull-requests" => "write" }
fail_workflow("permissions must be exactly #{expected_permissions.inspect}") unless workflow["permissions"] == expected_permissions
fail_workflow("must not use secrets") if workflow_values(workflow).any? { |value| value.match?(/\bsecrets\s*(?:\.|\[)/) }

jobs = workflow["jobs"]
fail_workflow("jobs must be a mapping") unless jobs.is_a?(Hash)
jobs.each_value do |candidate|
    fail_workflow("each job must be a mapping") unless candidate.is_a?(Hash)
    fail_workflow("jobs must not override permissions") if candidate.key?("permissions")
    next unless candidate["steps"].is_a?(Array)

    candidate["steps"].each do |step|
        fail_workflow("each step must be a mapping") unless step.is_a?(Hash)
        fail_workflow("must not check out code") if step["uses"].to_s.match?(/checkout/i) || step["run"].to_s.match?(/\bcheckout\b/i)
    end
end

job = jobs["enable-auto-merge"]
fail_workflow("must define enable-auto-merge") unless job.is_a?(Hash)
expected_guard = "github.event.pull_request.user.login == 'dependabot[bot]' && github.event.pull_request.base.ref == 'main'"
actual_guard = job["if"].to_s.gsub(/\s+/, " ").strip
fail_workflow("must guard Dependabot PRs targeting main") unless actual_guard == expected_guard

steps = job["steps"]
fail_workflow("enable-auto-merge steps must be an array") unless steps.is_a?(Array)
step_indexes = lambda { |id| steps.each_index.select { |index| steps[index]["id"] == id } }
scope_indexes = step_indexes.call("scope")
metadata_indexes = step_indexes.call("dependabot-metadata")
eligibility_indexes = step_indexes.call("eligibility")
fail_workflow("must contain exactly one scope step") unless scope_indexes.size == 1
fail_workflow("must contain exactly one metadata step") unless metadata_indexes.size == 1
fail_workflow("must contain exactly one eligibility step") unless eligibility_indexes.size == 1

scope_index = scope_indexes.first
metadata_index = metadata_indexes.first
eligibility_index = eligibility_indexes.first
scope = steps[scope_index]
metadata = steps[metadata_index]
eligibility = steps[eligibility_index]
scope_condition = "steps.scope.outputs.is_maven_update == 'true'"
scope_run = scope["run"].to_s
fail_workflow("scope must list pull request files through the GitHub API") unless scope_run.include?('gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files"')
fail_workflow("scope must reject non-pom.xml files") unless scope_run.include?("grep -Evq '(^|/)pom\\.xml$'")
fail_workflow("metadata must run after scope") unless metadata_index > scope_index
fail_workflow("metadata action must use the pinned v3 SHA") unless metadata["uses"] == "dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98"
fail_workflow("metadata must require Maven-only scope") unless metadata["if"] == scope_condition
fail_workflow("eligibility must run after metadata") unless eligibility_index > metadata_index
fail_workflow("eligibility must require Maven-only scope") unless eligibility["if"] == scope_condition
fail_workflow("eligibility must read dependency names") unless eligibility.dig("env", "DEPENDENCY_NAMES") == "${{ steps.dependabot-metadata.outputs.dependency-names }}"

protected_coordinates = [
    "org.springframework.boot:spring-boot-dependencies",
    "org.springframework.boot:spring-boot-starter-parent",
    "org.springframework.boot:spring-boot-maven-plugin",
    "org.springframework.cloud:spring-cloud-dependencies",
    "com.alibaba.cloud:spring-cloud-alibaba-dependencies"
]
eligibility_run = eligibility["run"].to_s
protected_coordinates.each do |coordinate|
    fail_workflow("eligibility must protect #{coordinate}") unless eligibility_run.include?(coordinate)
end
fail_workflow("eligibility must reject protected coordinates") unless eligibility_run.include?("is_eligible=false")
fail_workflow("eligibility must allow other coordinates") unless eligibility_run.include?("is_eligible=true")

merge_indexes = steps.each_index.select { |index| steps[index]["run"].to_s.match?(/\bgh\s+pr\s+merge\b/) }
fail_workflow("must contain exactly one gh pr merge step") unless merge_indexes.size == 1
merge_index = merge_indexes.first
merge = steps[merge_index]
expected_merge_condition = "steps.scope.outputs.is_maven_update == 'true' && steps.eligibility.outputs.is_eligible == 'true'"
actual_merge_condition = merge["if"].to_s.gsub(/\s+/, " ").strip
fail_workflow("merge must require Maven-only scope and eligibility") unless actual_merge_condition == expected_merge_condition
fail_workflow("merge must run after eligibility") unless merge_index > eligibility_index
merge_run = merge["run"].to_s
fail_workflow("merge must specify the repository") unless merge_run.match?(/--repo\s+"?\$\{REPOSITORY\}"?/)
fail_workflow("merge must queue auto-merge") unless merge_run.match?(/(?:^|\s)--auto(?:\s|$)/)
fail_workflow("merge must use rebase") unless merge_run.match?(/(?:^|\s)--rebase(?:\s|$)/)
fail_workflow("merge must define REPOSITORY") unless merge.dig("env", "REPOSITORY") == "${{ github.repository }}"
fail_workflow("must not gate by update type") if source.include?("UPDATE_TYPE") || source.include?("version-update:semver-patch")
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
require_text ".github/workflows/auto-merge-dependabot.yml" "dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3"
verify_dependabot_auto_merge_workflow
if grep -Fq -- "-DforceStdout | tail -n 1" "${root_dir}/.github/workflows/snapshot.yml"; then
    echo ".github/workflows/snapshot.yml must not use bare Maven 4 version extraction" >&2
    exit 1
fi

echo "Supply-chain workflow verification passed: ${root_dir}"
