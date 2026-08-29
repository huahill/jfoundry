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
require_text "contents: write"
require_text "PARENT_VERSION: 1.0.0"
require_text 'require_central_status "jfoundry-spring-boot-parent" "404"'
require_text 'require_central_status "jfoundry-dependencies" "200"'
require_text 'require_central_status "jfoundry-spring-dependencies" "200"'
require_text "404)"
require_text "200)"
require_text "Verify signed historical Parent POM with Maven 4"
require_text "HISTORICAL_SOURCE_COMMIT: 3eb6c53833fcbca26a4107c0d6aec6d4afde1a77"
require_text "HISTORICAL_POM_REPOSITORY_PATH: jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"
require_text "HISTORICAL_POM_SHA256: 1856dbb984e2a9985c9a0f1ae3fd777a6c736142f3a085cf33696422a870598f"
require_text "- name: Configure remediation workspace"
require_text 'remediation_evidence_root="${RUNNER_TEMP}/jfoundry-parent-remediation-source"'
require_text 'echo "REMEDIATION_EVIDENCE_ROOT=${remediation_evidence_root}" >> "${GITHUB_ENV}"'
require_text 'echo "PARENT_POM_PATH=${remediation_evidence_root}/pom.xml" >> "${GITHUB_ENV}"'
require_text 'git merge-base --is-ancestor "${HISTORICAL_SOURCE_COMMIT}" HEAD'
require_text 'git show "${HISTORICAL_SOURCE_COMMIT}:${HISTORICAL_POM_REPOSITORY_PATH}" > "${PARENT_POM_PATH}"'
require_text '"${HISTORICAL_POM_SHA256}" "${PARENT_POM_PATH}" | sha256sum --check --status'
require_text 'expected_coordinate = ["io.github.xfoundries", "jfoundry-spring-boot-parent", expected_version]'
require_text '"jfoundry-dependencies", "${project.version}", "pom", "import"'
require_text '"jfoundry-spring-dependencies", "${project.version}", "pom", "import"'
require_text 'value.call(project.elements["scm"], "tag") == "v#{expected_version}"'
require_text './mvnw -B -f "${PARENT_POM_PATH}"'
require_text "jfoundry-parent-remediation-deployment"
require_text "deploymentId: ([[:alnum:]-]+)"
require_text "central-deploy.log"
require_text "actions/upload-artifact"
require_text "actions/attest-build-provenance"
require_text "subject-path: remediation-evidence.tar.gz"
require_text 'find "${REMEDIATION_EVIDENCE_ROOT}" -name '\''*.asc'\'' -type f'
require_text 'historical_source_commit=${HISTORICAL_SOURCE_COMMIT}'
require_text 'historical_pom_sha256=${HISTORICAL_POM_SHA256}'
require_text 'git tag -fa "v${PARENT_VERSION}" -m "JFoundry v${PARENT_VERSION}" "${HISTORICAL_SOURCE_COMMIT}"'
require_text 'git push origin "refs/tags/v${PARENT_VERSION}" --force'
require_text 'gh release delete "v${PARENT_VERSION}" --yes'
require_text 'gh release create "v${PARENT_VERSION}" --verify-tag'
require_text "- name: Rewrite v1.0.0 GitHub tag and Release"
require_text "        if: success()"
forbid_text 'PARENT_POM_PATH: jfoundry-boms/jfoundry-spring-boot-parent/pom.yaml'
forbid_text '${{ runner.temp }}'
forbid_text 'bash scripts/generate-maven3-publication-tree.sh'
forbid_text 'MAVEN_3_VERSION'
forbid_text 'MAVEN_3_SHA512'
forbid_text 'git tag -fa "v${PARENT_VERSION}" -m "JFoundry v${PARENT_VERSION}" "${GITHUB_SHA}"'

workspace_configuration_line="$(grep -n -F -- "- name: Configure remediation workspace" "${workflow_file}" | head -n 1 | cut -d: -f1)"
request_verification_line="$(grep -n -F -- "- name: Verify remediation publication request" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${workspace_configuration_line}" || -z "${request_verification_line}" ||
      "${workspace_configuration_line}" -ge "${request_verification_line}" ]]; then
    echo "Spring Boot parent remediation workspace must be configured before request verification." >&2
    exit 1
fi

echo "Spring Boot parent remediation workflow verification passed: ${workflow_file}"
