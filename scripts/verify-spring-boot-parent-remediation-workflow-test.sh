#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="${root_dir}/scripts/verify-spring-boot-parent-remediation-workflow.sh"
workflow="${root_dir}/.github/workflows/publish-spring-boot-parent-1.0.0.yml"
temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-parent-remediation-test.XXXXXX")"
trap 'rm -rf "${temporary_root}"' EXIT

assert_rejects_without() {
    local pattern="$1"
    local name="$2"
    local candidate="${temporary_root}/${name}.yml"

    grep -v -- "${pattern}" "${workflow}" > "${candidate}"
    if bash "${verifier}" "${candidate}" >/dev/null 2>&1; then
        echo "Expected remediation workflow verification to reject ${name}." >&2
        exit 1
    fi
}

bash "${verifier}" "${workflow}"
assert_rejects_without "HISTORICAL_SOURCE_COMMIT:" "missing-historical-source-commit"
assert_rejects_without "HISTORICAL_POM_SHA256:" "missing-historical-pom-sha256"
assert_rejects_without 'git show "${HISTORICAL_SOURCE_COMMIT}:${HISTORICAL_POM_REPOSITORY_PATH}"' \
    "missing-historical-pom-materialization"
assert_rejects_without '"${{ steps.maven_3.outputs.executable }}" -B -f "${PARENT_POM_PATH}"' \
    "missing-maven3-verification"
assert_rejects_without 'REMEDIATION_EVIDENCE_ROOT:' "missing-remediation-evidence-root"

wrong_tag_target="${temporary_root}/wrong-tag-target.yml"
sed 's/ "${HISTORICAL_SOURCE_COMMIT}"$/ "${GITHUB_SHA}"/' "${workflow}" > "${wrong_tag_target}"
if bash "${verifier}" "${wrong_tag_target}" >/dev/null 2>&1; then
    echo "Expected remediation workflow verification to reject a tag target other than the historical source." >&2
    exit 1
fi

echo "Spring Boot parent remediation workflow verification tests passed."
