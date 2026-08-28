#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
converter="${repository_root}/scripts/convert-mason-reactor.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-reactor-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

write_pom() {
    local path="$1"
    local artifact_id="$2"
    local parent="${3:-}"
    mkdir -p "$(dirname "${path}")"
    {
        printf '%s\n' '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        printf '%s\n' '  <modelVersion>4.0.0</modelVersion>'
        if [[ -n "${parent}" ]]; then
            printf '%s\n' '  <parent>'
            printf '%s\n' '    <groupId>example</groupId>'
            printf '%s\n' '    <artifactId>root</artifactId>'
            printf '%s\n' '    <version>1.0.0</version>'
            printf '    <relativePath>%s</relativePath>\n' "${parent}"
            printf '%s\n' '  </parent>'
        else
            printf '%s\n' '  <groupId>example</groupId>'
            printf '%s\n' '  <version>1.0.0</version>'
        fi
        printf '  <artifactId>%s</artifactId>\n' "${artifact_id}"
        printf '%s\n' '</project>'
    } > "${path}"
}

valid_root="${fixture_root}/valid"
write_pom "${valid_root}/pom.xml" root
write_pom "${valid_root}/child/pom.xml" child ../pom.xml
mkdir -p "${valid_root}/existing"
printf '%s\n' \
    'modelVersion: 4.0.0' \
    'parent:' \
    '  relativePath: ../pom.xml' \
    'artifactId: existing-yaml' \
    > "${valid_root}/existing/pom.yaml"
write_pom \
    "${valid_root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.xml" \
    consumer

"${converter}" "${valid_root}"
second_run_output="$("${converter}" "${valid_root}")"
[[ "${second_run_output}" == *"Converted 0 Maven reactor POMs"* ]]

[[ -f "${valid_root}/pom.yaml" ]]
[[ ! -e "${valid_root}/pom.xml" ]]
[[ -f "${valid_root}/child/pom.yaml" ]]
[[ ! -e "${valid_root}/child/pom.xml" ]]
grep -Fq 'relativePath: ../pom.yaml' "${valid_root}/child/pom.yaml"
grep -Fq 'relativePath: ../pom.yaml' "${valid_root}/existing/pom.yaml"
[[ -f "${valid_root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.xml" ]]
[[ ! -e "${valid_root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.yaml" ]]

shadow_root="${fixture_root}/shadow"
write_pom "${shadow_root}/pom.xml" root
printf '%s\n' 'modelVersion: 4.0.0' > "${shadow_root}/pom.yaml"
if "${converter}" "${shadow_root}" >/dev/null 2>&1; then
    echo "Reactor converter accepted an XML/YAML shadow pair." >&2
    exit 1
fi
[[ -f "${shadow_root}/pom.xml" ]]
[[ -f "${shadow_root}/pom.yaml" ]]

echo "Mason reactor converter test passed."
