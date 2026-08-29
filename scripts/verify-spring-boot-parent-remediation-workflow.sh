#!/usr/bin/env bash

set -euo pipefail

workflow_file="${1:-.github/workflows/publish-spring-boot-parent-1.0.0.yml}"
parent_pom_file="jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"

if [[ ! -f "${workflow_file}" ]]; then
    echo "Spring Boot parent remediation workflow does not exist: ${workflow_file}" >&2
    exit 1
fi

if [[ ! -f "${parent_pom_file}" ]]; then
    echo "Spring Boot parent POM does not exist: ${parent_pom_file}" >&2
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
require_text "contents: write"
require_text "PARENT_VERSION: 1.0.0"
require_text 'require_central_status "jfoundry-spring-boot-parent" "404"'
require_text 'require_central_status "jfoundry-dependencies" "200"'
require_text 'require_central_status "jfoundry-spring-dependencies" "200"'
require_text "404)"
require_text "200)"
require_text '-f jfoundry-boms/jfoundry-spring-boot-parent/pom.xml -Prelease'
require_text '          ./mvnw -B -f jfoundry-boms/jfoundry-spring-boot-parent/pom.xml \'
require_text "central-deploy.log"
require_text "deploymentId: ([[:alnum:]-]+)"
require_text "central-deploy.log"
require_text "actions/upload-artifact"
require_text "actions/attest-build-provenance"
require_text "subject-path: remediation-evidence.tar.gz"
require_text 'git tag -fa "v${PARENT_VERSION}"'
require_text 'git push origin "refs/tags/v${PARENT_VERSION}" --force'
require_text 'gh release delete "v${PARENT_VERSION}" --yes'
require_text 'gh release create "v${PARENT_VERSION}" --verify-tag'
require_text "- name: Rewrite v1.0.0 GitHub tag and Release"
require_text "        if: success()"
if ! grep -Fq -- "<tag>v1.0.0</tag>" "${parent_pom_file}"; then
    echo "Spring Boot parent POM must identify v1.0.0 as its SCM tag." >&2
    exit 1
fi

forbid_text "MAVEN_3_VERSION"
forbid_text "MAVEN_3_SHA512"
forbid_text "steps.maven_3"

echo "Spring Boot parent remediation workflow verification passed: ${workflow_file}"
