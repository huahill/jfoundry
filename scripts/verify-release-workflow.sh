#!/usr/bin/env bash

set -euo pipefail

workflow_file="${1:-.github/workflows/release.yml}"

if [[ ! -f "${workflow_file}" ]]; then
    echo "Release workflow does not exist: ${workflow_file}" >&2
    exit 1
fi

require_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Release workflow must contain: ${text}" >&2
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
require_text "Verify immutable release source"
require_text "Verify complete CI"
require_text 'test "${{ inputs.release_tag }}" = "v${version}"'
require_text 'test -z "$(git status --porcelain)"'
require_text "gh run view"
require_text "actions: read"
require_text "-Prelease -DskipTests verify"
require_text "-Prelease -DskipTests deploy"
require_text "Verify Dependabot security alerts"
require_text "dependabot/alerts"
require_text "security-events: read"
require_text "vulnerability-alerts: read"
require_text "attestations: write"
require_text "id-token: write"
require_text "actions/attest-build-provenance"
require_text "central-deploy.log"
require_text "release-evidence/poms"
require_text "release-evidence/signatures"
require_text "release-metadata.txt"
require_text "target/*.asc"

forbid_text "versions-maven-plugin"
forbid_text "versions:set"
forbid_text "git push"

echo "Release workflow verification passed: ${workflow_file}"
