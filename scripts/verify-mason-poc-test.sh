#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="${script_dir}/verify-mason-poc.sh"

if [[ ! -x "${verifier}" ]]; then
    echo "Mason PoC verifier does not exist or is not executable: ${verifier}" >&2
    exit 1
fi

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-poc-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

quarkus_test_modules=(
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-transaction-quarkus-runtime"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-domain-event-quarkus-runtime"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-persistence-quarkus-runtime"
)

write_valid_fixture() {
    local root="$1"
    local path

    rm -rf "${root}"
    mkdir -p "${root}/.mvn/wrapper" "${root}/.github/workflows" "${root}/child" "${root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer"
    printf '%s\n' '<extensions>' '  <extension>' '    <groupId>eu.maveniverse.maven.mason</groupId>' '    <artifactId>mason</artifactId>' '    <version>0.3.0</version>' '  </extension>' '</extensions>' > "${root}/.mvn/extensions.xml"
    printf '%s\n' 'wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar' 'distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-6/apache-maven-4.0.0-rc-6-bin.zip' > "${root}/.mvn/wrapper/maven-wrapper.properties"
    printf '%s\n' 'MAVEN_3_VERSION: 3.9.16' 'bash scripts/generate-maven3-publication-tree.sh "${publication_tree}"' 'cd "${publication_tree}"' > "${root}/.github/workflows/release.yml"
    printf '%s\n' \
        'modelVersion: 4.0.0' \
        'artifactId: root' \
        'modules:' \
        '  - child' \
        "  - ${quarkus_test_modules[0]}" \
        "  - ${quarkus_test_modules[1]}" \
        "  - ${quarkus_test_modules[2]}" \
        > "${root}/pom.yaml"
    printf '%s\n' 'modelVersion: 4.0.0' 'parent:' '  relativePath: ../pom.yaml' 'artifactId: child' > "${root}/child/pom.yaml"
    printf '%s\n' '<project><modelVersion>4.0.0</modelVersion></project>' > "${root}/jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.xml"

    for path in "${quarkus_test_modules[@]}"; do
        mkdir -p "${root}/${path}"
        printf '%s\n' 'modelVersion: 4.0.0' 'artifactId: quarkus-test-module' 'build:' '  plugins:' '    - groupId: io.quarkus' '      artifactId: quarkus-maven-plugin' '      extensions: true' '      configuration:' '        skipSourceGeneration: ${skipTests}' '      executions:' '        - goals:' '            - generate-code-tests' > "${root}/${path}/pom.yaml"
    done
}

expect_failure() {
    local expected_message="$1"
    local root="$2"
    local expected_count="${3:-5}"
    local output

    if output="$("${verifier}" "${root}" "${expected_count}" 2>&1)"; then
        echo "Expected verifier failure containing: ${expected_message}" >&2
        exit 1
    fi
    if [[ "${output}" != *"${expected_message}"* ]]; then
        echo "Verifier failed for an unexpected reason:" >&2
        echo "${output}" >&2
        exit 1
    fi
}

valid_fixture="${fixture_root}/valid"
write_valid_fixture "${valid_fixture}"
"${verifier}" "${valid_fixture}" 5
expect_failure "Expected 6 Mason YAML reactor POMs, found 5" "${valid_fixture}" 6

missing_extension="${fixture_root}/missing-extension"
write_valid_fixture "${missing_extension}"
rm "${missing_extension}/.mvn/extensions.xml"
expect_failure "Mason extension configuration does not exist" "${missing_extension}"

wrong_version="${fixture_root}/wrong-version"
write_valid_fixture "${wrong_version}"
sed -i.bak 's/<version>0.3.0<\//<version>0.2.0<\//' "${wrong_version}/.mvn/extensions.xml"
rm "${wrong_version}/.mvn/extensions.xml.bak"
expect_failure "Mason extension must use version 0.3.0" "${wrong_version}"

xml_shadow="${fixture_root}/xml-shadow"
write_valid_fixture "${xml_shadow}"
printf '%s\n' '<project/>' > "${xml_shadow}/child/pom.xml"
expect_failure "Converted project retains an XML shadow POM" "${xml_shadow}"

remaining_xml="${fixture_root}/remaining-xml"
write_valid_fixture "${remaining_xml}"
rm "${remaining_xml}/child/pom.yaml"
printf '%s\n' '<project/>' > "${remaining_xml}/child/pom.xml"
expect_failure "Reactor module does not contain pom.yaml" "${remaining_xml}" 4

unreachable_yaml="${fixture_root}/unreachable-yaml"
write_valid_fixture "${unreachable_yaml}"
mkdir -p "${unreachable_yaml}/orphan"
printf '%s\n' 'modelVersion: 4.0.0' 'artifactId: orphan' > "${unreachable_yaml}/orphan/pom.yaml"
expect_failure "Mason YAML POM is not reachable from the root reactor" "${unreachable_yaml}" 6

missing_reactor_module="${fixture_root}/missing-reactor-module"
write_valid_fixture "${missing_reactor_module}"
printf '%s\n' '  - missing' >> "${missing_reactor_module}/pom.yaml"
expect_failure "Reactor module does not contain pom.yaml" "${missing_reactor_module}"

duplicate_reactor_module="${fixture_root}/duplicate-reactor-module"
write_valid_fixture "${duplicate_reactor_module}"
printf '%s\n' '  - child' >> "${duplicate_reactor_module}/pom.yaml"
expect_failure "Reactor module is declared more than once" "${duplicate_reactor_module}"

absolute_reactor_module="${fixture_root}/absolute-reactor-module"
write_valid_fixture "${absolute_reactor_module}"
printf '%s\n' '  - /absolute/module' >> "${absolute_reactor_module}/pom.yaml"
expect_failure "Reactor module path must be relative" "${absolute_reactor_module}"

escaping_reactor_module="${fixture_root}/escaping-reactor-module"
write_valid_fixture "${escaping_reactor_module}"
printf '%s\n' '  - ../outside' >> "${escaping_reactor_module}/pom.yaml"
expect_failure "Reactor module path must not contain '..'" "${escaping_reactor_module}"

external_target="${fixture_root}/external-reactor-target"
mkdir -p "${external_target}"
printf '%s\n' 'modelVersion: 4.0.0' 'artifactId: external' > "${external_target}/pom.yaml"
external_reactor_module="${fixture_root}/external-reactor-module"
write_valid_fixture "${external_reactor_module}"
ln -s "${external_target}" "${external_reactor_module}/external"
printf '%s\n' '  - external' >> "${external_reactor_module}/pom.yaml"
expect_failure "Reactor module resolves outside repository root" "${external_reactor_module}"

cyclic_reactor_module="${fixture_root}/cyclic-reactor-module"
write_valid_fixture "${cyclic_reactor_module}"
ln -s "${cyclic_reactor_module}" "${cyclic_reactor_module}/child/root-link"
printf '%s\n' 'modules:' '  - root-link' >> "${cyclic_reactor_module}/child/pom.yaml"
expect_failure "Reactor module cycle detected" "${cyclic_reactor_module}"

wrong_parent="${fixture_root}/wrong-parent"
write_valid_fixture "${wrong_parent}"
sed -i.bak 's#../pom.yaml#../pom.xml#' "${wrong_parent}/child/pom.yaml"
rm "${wrong_parent}/child/pom.yaml.bak"
expect_failure "YAML parent path must resolve a YAML POM" "${wrong_parent}"

missing_quarkus_test_model="${fixture_root}/missing-quarkus-test-model"
write_valid_fixture "${missing_quarkus_test_model}"
sed -i.bak '/generate-code-tests/d' "${missing_quarkus_test_model}/${quarkus_test_modules[0]}/pom.yaml"
rm "${missing_quarkus_test_model}/${quarkus_test_modules[0]}/pom.yaml.bak"
expect_failure "Quarkus test module must generate the serialized application model" "${missing_quarkus_test_model}"

missing_quarkus_skip_tests="${fixture_root}/missing-quarkus-skip-tests"
write_valid_fixture "${missing_quarkus_skip_tests}"
sed -i.bak '/skipSourceGeneration/d' "${missing_quarkus_skip_tests}/${quarkus_test_modules[0]}/pom.yaml"
rm "${missing_quarkus_skip_tests}/${quarkus_test_modules[0]}/pom.yaml.bak"
expect_failure "Quarkus test model generation must honor skipTests" "${missing_quarkus_skip_tests}"

missing_publication_tree="${fixture_root}/missing-publication-tree"
write_valid_fixture "${missing_publication_tree}"
sed -i.bak '/generate-maven3-publication-tree/d' "${missing_publication_tree}/.github/workflows/release.yml"
rm "${missing_publication_tree}/.github/workflows/release.yml.bak"
expect_failure "Production Central publication must generate a Maven 3 XML tree" "${missing_publication_tree}"

echo "Mason PoC verifier self-test passed."
