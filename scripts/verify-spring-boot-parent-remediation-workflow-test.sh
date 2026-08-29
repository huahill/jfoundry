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
assert_rejects_without 'REMEDIATION_EVIDENCE_ROOT=' "missing-remediation-evidence-root-export"
assert_rejects_without 'PARENT_POM_PATH=' "missing-parent-pom-path-export"

job_env_runner_context="${temporary_root}/job-env-runner-context.yml"
awk '
    { print }
    /HISTORICAL_POM_SHA256:/ {
        print "      INVALID_JOB_ENV: ${{ runner.temp }}/invalid"
    }
' "${workflow}" > "${job_env_runner_context}"
if bash "${verifier}" "${job_env_runner_context}" >/dev/null 2>&1; then
    echo "Expected remediation workflow verification to reject runner context in job-level env." >&2
    exit 1
fi

late_workspace_configuration="${temporary_root}/late-workspace-configuration.yml"
ruby - "${workflow}" "${late_workspace_configuration}" <<'RUBY'
source, target = ARGV
content = File.read(source)
configure_marker = "      - name: Configure remediation workspace\n"
verify_marker = "      - name: Verify remediation publication request\n"
checkout_marker = "      - name: Checkout main\n"
configure_start = content.index(configure_marker) or abort "Missing workspace configuration step"
configure_end = content.index(verify_marker, configure_start) or abort "Missing request verification step"
configure_step = content.slice!(configure_start...configure_end)
checkout_start = content.index(checkout_marker) or abort "Missing checkout step"
content.insert(checkout_start, configure_step)
File.write(target, content)
RUBY
if bash "${verifier}" "${late_workspace_configuration}" >/dev/null 2>&1; then
    echo "Expected remediation workflow verification to require workspace configuration first." >&2
    exit 1
fi

wrong_tag_target="${temporary_root}/wrong-tag-target.yml"
sed 's/ "${HISTORICAL_SOURCE_COMMIT}"$/ "${GITHUB_SHA}"/' "${workflow}" > "${wrong_tag_target}"
if bash "${verifier}" "${wrong_tag_target}" >/dev/null 2>&1; then
    echo "Expected remediation workflow verification to reject a tag target other than the historical source." >&2
    exit 1
fi

echo "Spring Boot parent remediation workflow verification tests passed."
