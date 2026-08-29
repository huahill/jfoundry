#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="${root_dir}/scripts/verify-release-workflow.sh"
workflow="${root_dir}/.github/workflows/release.yml"
temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-release-workflow-test.XXXXXX")"
trap 'rm -rf "${temporary_root}"' EXIT

assert_rejects() {
    local candidate="$1"
    if bash "${verifier}" "${candidate}" >/dev/null 2>&1; then
        echo "Expected release workflow verification to reject ${candidate}." >&2
        exit 1
    fi
}

bash "${verifier}" "${workflow}"

missing_readiness="${temporary_root}/missing-readiness.yml"
grep -v 'Verify Maven 4 Central readiness' "${workflow}" > "${missing_readiness}"
assert_rejects "${missing_readiness}"

missing_flag="${temporary_root}/missing-readiness-flag.yml"
grep -v 'MAVEN_CENTRAL_MAVEN4_READY' "${workflow}" > "${missing_flag}"
assert_rejects "${missing_flag}"

legacy_maven3="${temporary_root}/legacy-maven3.yml"
awk '{ print; if ($0 ~ /name: Verify Maven 4 Central readiness/) print "        MAVEN_3_VERSION: 3.9.16" }' \
    "${workflow}" > "${legacy_maven3}"
assert_rejects "${legacy_maven3}"

workspace_override="${temporary_root}/workspace-override.yml"
sed 's#artifact_root="${GITHUB_WORKSPACE}"#artifact_root="${RUNNER_TEMP}"#' \
    "${workflow}" > "${workspace_override}"
assert_rejects "${workspace_override}"

echo "Release workflow verification tests passed."
