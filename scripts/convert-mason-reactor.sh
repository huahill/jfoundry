#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
converter="${script_dir}/support/convert-xml-pom-to-mason-yaml.rb"

if [[ "$#" -ne 1 ]]; then
    echo "Usage: $0 <repository-root>" >&2
    exit 2
fi

repository_root="$(cd "$1" && pwd -P)"
fixture_path="${repository_root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.xml"
source_poms=()
temporary_poms=()
source_count=0
temporary_count=0

cleanup() {
    local temporary_pom
    if [[ "${temporary_count}" -gt 0 ]]; then
        for temporary_pom in "${temporary_poms[@]}"; do
            [[ ! -e "${temporary_pom}" ]] || rm "${temporary_pom}"
        done
    fi
}
trap cleanup EXIT

while IFS= read -r source_pom; do
    if [[ "${source_pom}" != "${fixture_path}" ]]; then
        source_poms+=("${source_pom}")
        source_count=$((source_count + 1))
    fi
done < <(
    find "${repository_root}" -type f -name pom.xml \
        -not -path '*/target/*' \
        -not -path '*/graphify-out/*' \
        -not -path '*/.git/*' \
        -print | LC_ALL=C sort
)

if [[ "${source_count}" -gt 0 ]]; then
    for source_pom in "${source_poms[@]}"; do
        destination_pom="${source_pom%.xml}.yaml"
        if [[ -e "${destination_pom}" ]]; then
            echo "Refusing to overwrite an XML/YAML shadow pair: ${destination_pom}" >&2
            exit 1
        fi
    done

    for source_pom in "${source_poms[@]}"; do
        temporary_pom="${source_pom%.xml}.yaml.mason-tmp.$$"
        ruby "${converter}" "${source_pom}" "${temporary_pom}"
        temporary_poms+=("${temporary_pom}")
        temporary_count=$((temporary_count + 1))
    done

    for source_pom in "${source_poms[@]}"; do
        destination_pom="${source_pom%.xml}.yaml"
        temporary_pom="${source_pom%.xml}.yaml.mason-tmp.$$"
        mv "${temporary_pom}" "${destination_pom}"
        rm "${source_pom}"
    done
fi

while IFS= read -r yaml_pom; do
    sed -i.bak -E \
        's#(relativePath:[[:space:]]+[^#]*)pom\.xml#\1pom.yaml#' \
        "${yaml_pom}"
    rm "${yaml_pom}.bak"
done < <(
    find "${repository_root}" -type f -name pom.yaml \
        -not -path '*/target/*' \
        -not -path '*/graphify-out/*' \
        -print | LC_ALL=C sort
)

echo "Converted ${source_count} Maven reactor POMs to Mason YAML."
