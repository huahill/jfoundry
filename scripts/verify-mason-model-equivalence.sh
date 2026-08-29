#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
normalizer_xslt="${repository_root}/scripts/support/normalize-mason-model.xsl"

normalize_model() {
    local source_file="$1"
    local destination_file="$2"

    xsltproc "${normalizer_xslt}" "${source_file}" |
        xmllint --xpath '/*' - |
        xmllint --c14n - |
        sed -E \
            -e 's#http://maven\.apache\.org/POM/4\.1\.0#http://maven.apache.org/POM/4.0.0#g' \
            -e 's#https://maven\.apache\.org/xsd/maven-4\.1\.0\.xsd#https://maven.apache.org/xsd/maven-4.0.0.xsd#g' \
            -e 's#<modelVersion>4\.1\.0</modelVersion>#<modelVersion>4.0.0</modelVersion>#g' \
            -e 's#(<relativePath>[^<]*)pom\.yaml(</relativePath>)#\1pom.xml\2#g' \
            > "${destination_file}"
}

report_first_difference() {
    local baseline_file="$1"
    local candidate_file="$2"
    local difference_offset
    local context_start

    difference_offset="$(cmp -l "${baseline_file}" "${candidate_file}" 2>/dev/null | awk 'NR == 1 { print $1; exit }' || true)"
    if [[ -z "${difference_offset}" ]]; then
        difference_offset="$(wc -c < "${baseline_file}" | tr -d ' ')"
    fi
    context_start=$((difference_offset > 240 ? difference_offset - 240 : 0))

    echo "First normalized model difference at byte ${difference_offset}." >&2
    for model_side in baseline candidate; do
        local model_file="${baseline_file}"
        if [[ "${model_side}" == "candidate" ]]; then
            model_file="${candidate_file}"
        fi
        echo "${model_side} context:" >&2
        dd if="${model_file}" bs=1 skip="${context_start}" count=480 2>/dev/null |
            fold -w 120 >&2
        echo >&2
    done
}

compare_models() {
    local baseline_file="$1"
    local candidate_file="$2"
    local comparison_root
    local baseline_normalized
    local candidate_normalized

    comparison_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-model-compare.XXXXXX")"
    baseline_normalized="${comparison_root}/baseline.xml"
    candidate_normalized="${comparison_root}/candidate.xml"
    normalize_model "${baseline_file}" "${baseline_normalized}"
    normalize_model "${candidate_file}" "${candidate_normalized}"

    if ! cmp -s "${baseline_normalized}" "${candidate_normalized}"; then
        report_first_difference "${baseline_normalized}" "${candidate_normalized}"
        rm -rf "${comparison_root}"
        echo "Maven model difference: ${baseline_file} != ${candidate_file}" >&2
        return 1
    fi
    rm -rf "${comparison_root}"
}

if [[ "${1:-}" == "--compare-files" ]]; then
    if [[ "$#" -ne 3 ]]; then
        echo "Usage: $0 --compare-files <baseline.xml> <candidate.xml>" >&2
        exit 2
    fi
    compare_models "$2" "$3"
    exit 0
fi

baseline_ref="${1:-origin/main}"
candidate_ref="${2:-WORKTREE}"
git -C "${repository_root}" rev-parse --verify "${baseline_ref}^{commit}" >/dev/null
if [[ "${candidate_ref}" != "WORKTREE" ]]; then
    git -C "${repository_root}" rev-parse --verify "${candidate_ref}^{commit}" >/dev/null
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-models.XXXXXX")"
workspace="${temporary_root}/workspace"
model_root="${temporary_root}/models"
mkdir -p "${workspace}" "${model_root}/baseline" "${model_root}/candidate"
trap 'rm -rf "${temporary_root}"' EXIT

clear_workspace() {
    find "${workspace}" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
}

extract_ref() {
    local ref="$1"

    clear_workspace
    git -C "${repository_root}" archive "${ref}" | tar -x -C "${workspace}"
}

extract_worktree() {
    clear_workspace
    rsync -a \
        --exclude '/.git/' \
        --exclude '/.idea/' \
        --exclude '/.vscode/' \
        --exclude '/.codegraph/' \
        --exclude '/graphify-out/' \
        --exclude '/target/' \
        --exclude '*/target/' \
        "${repository_root}/" "${workspace}/"
}

generate_models() {
    local side="$1"
    local output_file="${model_root}/${side}/reactor.xml"
    local log_file="${model_root}/${side}/reactor.log"

    if ! (cd "${workspace}" && ./mvnw -q help:effective-pom \
        "-Doutput=${output_file}") > "${log_file}" 2>&1; then
        cat "${log_file}" >&2
        echo "Failed to generate ${side} aggregate effective model." >&2
        exit 1
    fi
}

extract_ref "${baseline_ref}"
generate_models "baseline"
if [[ "${candidate_ref}" == "WORKTREE" ]]; then
    extract_worktree
else
    extract_ref "${candidate_ref}"
fi
generate_models "candidate"

baseline_count="$(xmllint --xpath \
    'count(/*[local-name()="projects"]/*[local-name()="project"])' \
    "${model_root}/baseline/reactor.xml")"
candidate_count="$(xmllint --xpath \
    'count(/*[local-name()="projects"]/*[local-name()="project"])' \
    "${model_root}/candidate/reactor.xml")"
if [[ "${baseline_count}" == "0" || "${baseline_count}" != "${candidate_count}" ]]; then
    echo "Maven reactor project count differs: baseline=${baseline_count}, candidate=${candidate_count}" >&2
    exit 1
fi

compare_models \
    "${model_root}/baseline/reactor.xml" \
    "${model_root}/candidate/reactor.xml"

echo "Equivalent Maven models: ${candidate_count} reactor projects"
echo "Mason model equivalence verification passed: ${baseline_ref} == ${candidate_ref}"
