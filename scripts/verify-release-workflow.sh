#!/usr/bin/env bash

set -euo pipefail

workflow_file="${1:-.github/workflows/release.yml}"
pom_file="${2:-pom.xml}"
bom_pom_files=(
    "jfoundry-boms/jfoundry-dependencies/pom.xml"
    "jfoundry-boms/jfoundry-foundation-dependencies/pom.xml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.xml"
    "jfoundry-boms/jfoundry-modules-dependencies/pom.xml"
    "jfoundry-boms/jfoundry-quarkus-dependencies/pom.xml"
    "jfoundry-boms/jfoundry-spring-dependencies/pom.xml"
)

if [[ ! -f "${workflow_file}" ]]; then
    echo "Release workflow does not exist: ${workflow_file}" >&2
    exit 1
fi

if [[ ! -f "${pom_file}" ]]; then
    echo "Release Maven configuration does not exist: ${pom_file}" >&2
    exit 1
fi

require_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Release workflow must contain: ${text}" >&2
        exit 1
    fi
}

require_count() {
    local text="$1"
    local expected_count="$2"
    local actual_count

    actual_count="$(grep -Fc -- "${text}" "${workflow_file}" || true)"
    if [[ "${actual_count}" != "${expected_count}" ]]; then
        echo "Release workflow must contain ${expected_count} occurrence(s) of: ${text}" >&2
        exit 1
    fi
}

require_pom_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${pom_file}"; then
        echo "Release Maven configuration must contain: ${text}" >&2
        exit 1
    fi
}

forbid_text() {
    local text="$1"
    if grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Release workflow must not contain: ${text}" >&2
        exit 1
    fi
}

require_text "workflow_dispatch:"
require_text "release_tag:"
require_text 'ref: ${{ inputs.release_tag }}'
require_text 'test "${GITHUB_REF}" = "refs/heads/main"'
require_text "Verify immutable release source"
require_text "Verify complete CI"
require_text 'test "${{ inputs.release_tag }}" = "v${version}"'
require_text 'test -z "$(git status --porcelain)"'
require_count "sed -n 's/^\\[INFO\\] \\[stdout\\] //p'" 2
require_text "gh run view"
require_text "actions: read"
require_text "contents: write"
require_text 'GH_TOKEN: ${{ github.token }}'
require_text "-Prelease -DskipTests verify"
require_text "Verify Maven Central Consumer POMs"
require_text "verify-consumer-pom.sh"
require_text "Install Apache Maven 3 for Central publication"
require_text "MAVEN_3_VERSION: 3.9.16"
require_text "MAVEN_3_SHA512:"
require_text '"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests deploy'
require_text '-DaltDeploymentRepository=jfoundry::file:${RUNNER_TEMP}/jfoundry-release-deployment'
require_text "deployment_id="
require_text "deploymentId: ([[:alnum:]-]+)"
require_text 'if [[ -z "${deployment_id}" ]]'
require_text "Check Maven Central publication"
require_text "Verify Maven Central publication"
require_text "repo.maven.apache.org/maven2"
require_text 'jfoundry-parent-${RELEASE_VERSION}.pom'
require_text "git merge-base --is-ancestor"
require_text "origin/main"
require_text "git fetch origin +refs/heads/main:refs/remotes/origin/main --no-tags"
require_text 'central_status="$('
require_text "--write-out '%{http_code}'"
require_text "200)"
require_text "404)"
require_text "Central publication lookup returned unexpected HTTP status"
require_text "Verify Dependabot security alerts"
require_text "dependabot/alerts"
require_text "security-events: read"
require_text "vulnerability-alerts: read"
require_text "attestations: write"
require_text "id-token: write"
require_text "actions/attest-build-provenance"
require_text "central-deploy.log"
require_text "Central Publishing did not report a deploymentId."
require_text "release-evidence/consumer-poms"
require_text "release-evidence/signatures"
require_text "release-metadata.txt"
require_text "target/*.asc"
require_text "! -path '*/target/project-local-repo/*'"
require_text "central-deployment.txt"
require_text "Archive release evidence"
require_text "tar -czf release-evidence.tar.gz release-evidence"
require_text "subject-path: release-evidence.tar.gz"
require_text "gh release create"
require_text "gh release edit"
require_text "--verify-tag"
require_text "--draft=false"
require_text "is_prerelease=false"
require_text "--prerelease"
require_text "--latest=false"
require_text "always()"

require_pom_text "<autoPublish>true</autoPublish>"
require_pom_text "<waitUntil>PUBLISHED</waitUntil>"
for bom_pom_file in "${bom_pom_files[@]}"; do
    if [[ ! -f "${bom_pom_file}" ]]; then
        echo "Standalone BOM release Maven configuration does not exist: ${bom_pom_file}" >&2
        exit 1
    fi
    pom_file="${bom_pom_file}"
    require_pom_text "<autoPublish>true</autoPublish>"
    require_pom_text "<waitUntil>PUBLISHED</waitUntil>"
done

central_verification_line="$(grep -n -F "Verify Maven Central publication" "${workflow_file}" | head -n 1 | cut -d: -f1)"
github_release_line="$(grep -n -F "Create GitHub Release" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${central_verification_line}" || -z "${github_release_line}" || "${github_release_line}" -le "${central_verification_line}" ]]; then
    echo "GitHub Release creation must follow Maven Central publication verification." >&2
    exit 1
fi

forbid_text "versions-maven-plugin"
forbid_text "versions:set"
forbid_text "git push"
forbid_text "org.sonatype.central:central-publishing-maven-plugin:0.11.0:publish"
forbid_text "./mvnw -B -Prelease -DskipTests verify \\"
forbid_text "-DforceStdout | tail -n 1"
forbid_text "search.maven.org/solrsearch/select"
forbid_text "subject-path: release-evidence/**"

release_evidence_archive_line="$(grep -n -F "Archive release evidence" "${workflow_file}" | head -n 1 | cut -d: -f1)"
provenance_line="$(grep -n -F "Attest release artifact provenance" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${release_evidence_archive_line}" || -z "${provenance_line}" || "${provenance_line}" -le "${release_evidence_archive_line}" ]]; then
    echo "Release evidence must be archived before provenance attestation." >&2
    exit 1
fi

echo "Release workflow verification passed: ${workflow_file}"
