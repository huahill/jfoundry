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

require_file ".github/dependabot.yml"
require_file ".github/workflows/codeql.yml"
require_file ".github/workflows/release.yml"
require_file ".github/workflows/ci.yml"
require_file ".github/workflows/snapshot.yml"
require_file ".github/workflows/auto-merge-dependabot.yml"

require_text ".github/dependabot.yml" "package-ecosystem: maven"
require_text ".github/dependabot.yml" "package-ecosystem: github-actions"
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
