#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

normalize_model() {
    local source_file="$1"
    local destination_file="$2"

    xmllint --xpath '/*' "${source_file}" |
        xmllint --c14n - |
        sed 's#<relativePath>../../pom.yaml</relativePath>#<relativePath>../../pom.xml</relativePath>#' \
            > "${destination_file}"
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

    if ! diff -u "${baseline_normalized}" "${candidate_normalized}"; then
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

projects=(
    "root:pom.xml:pom.yaml"
    "domain:jfoundry-core/jfoundry-domain/pom.xml:jfoundry-core/jfoundry-domain/pom.yaml"
    "starter:jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.xml:jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.yaml"
    "helidon-bom:jfoundry-boms/jfoundry-helidon-dependencies/pom.xml:jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml"
)

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
    local path_field="$2"
    local entry label xml_path yaml_path project_path output_file log_file

    for entry in "${projects[@]}"; do
        IFS=: read -r label xml_path yaml_path <<< "${entry}"
        if [[ "${path_field}" == "xml" ]]; then
            project_path="${xml_path}"
        else
            project_path="${yaml_path}"
        fi
        output_file="${model_root}/${side}/${label}.xml"
        log_file="${model_root}/${side}/${label}.log"
        if ! (cd "${workspace}" && ./mvnw -q -N -f "${project_path}" \
            help:effective-pom "-Doutput=${output_file}") > "${log_file}" 2>&1; then
            cat "${log_file}" >&2
            echo "Failed to generate ${side} effective model for ${label}." >&2
            exit 1
        fi
    done
}

extract_ref "${baseline_ref}"
generate_models "baseline" "xml"
if [[ "${candidate_ref}" == "WORKTREE" ]]; then
    extract_worktree
else
    extract_ref "${candidate_ref}"
fi
generate_models "candidate" "yaml"

for entry in "${projects[@]}"; do
    IFS=: read -r label _ _ <<< "${entry}"
    compare_models "${model_root}/baseline/${label}.xml" "${model_root}/candidate/${label}.xml"
    echo "Equivalent Maven model: ${label}"
done

echo "Mason model equivalence verification passed: ${baseline_ref} == ${candidate_ref}"
