#!/usr/bin/env bash

set -euo pipefail

workflow_file="${1:-.github/workflows/publish-spring-boot-parent-1.0.0.yml}"

if [[ ! -f "${workflow_file}" ]]; then
    echo "Spring Boot parent remediation workflow does not exist: ${workflow_file}" >&2
    exit 1
fi

require_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Spring Boot parent remediation workflow must contain: ${text}" >&2
        exit 1
    fi
}

forbid_text() {
    local text="$1"
    if grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Spring Boot parent remediation workflow must not contain: ${text}" >&2
        exit 1
    fi
}

require_text "workflow_dispatch:"
require_text "confirmation:"
require_text "PUBLISH_JFOUNDRY_SPRING_BOOT_PARENT_1_0_0"
require_text 'test "${GITHUB_REF}" = "refs/heads/main"'
require_text 'test "${CONFIRMATION}" = "PUBLISH_JFOUNDRY_SPRING_BOOT_PARENT_1_0_0"'
require_text "environment: jfoundry"
require_text "PARENT_VERSION: 1.0.0"
require_text 'require_central_status "jfoundry-spring-boot-parent" "404"'
require_text 'require_central_status "jfoundry-dependencies" "200"'
require_text 'require_central_status "jfoundry-spring-dependencies" "200"'
require_text "404)"
require_text "200)"
require_text "MAVEN_3_VERSION: 3.9.16"
require_text "MAVEN_3_SHA512:"
require_text '-f jfoundry-boms/jfoundry-spring-boot-parent/pom.xml -Prelease'
require_text "jfoundry-parent-remediation-deployment"
require_text "deploymentId: ([[:alnum:]-]+)"
require_text "central-deploy.log"
require_text "actions/upload-artifact"
require_text "actions/attest-build-provenance"
require_text "subject-path: remediation-evidence.tar.gz"
forbid_text "gh release create"
forbid_text "gh release edit"
forbid_text "git tag"
forbid_text "git push"

echo "Spring Boot parent remediation workflow verification passed: ${workflow_file}"
