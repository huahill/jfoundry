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

converted_poms=(
    "pom.yaml"
    "jfoundry-core/jfoundry-domain/pom.yaml"
    "jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.yaml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml"
)

xml_parent_paths=(
    "jfoundry-core/jfoundry-application/pom.xml:../../pom.yaml"
    "jfoundry-core/jfoundry-architecture/jfoundry-architecture-test/pom.xml:../../../pom.yaml"
    "jfoundry-core/jfoundry-architecture/pom.xml:../../pom.yaml"
    "jfoundry-core/jfoundry-infrastructure/pom.xml:../../pom.yaml"
    "jfoundry-runtime/jfoundry-helidon/pom.xml:../../pom.yaml"
    "jfoundry-runtime/jfoundry-jakarta/pom.xml:../../pom.yaml"
    "jfoundry-runtime/jfoundry-quarkus/pom.xml:../../pom.yaml"
    "jfoundry-runtime/jfoundry-spring/pom.xml:../../pom.yaml"
)

write_valid_fixture() {
    local root="$1"
    local entry path relative_path

    rm -rf "${root}"
    mkdir -p "${root}/.mvn/wrapper" "${root}/.github/workflows"
    printf '%s\n' \
        '<extensions>' \
        '  <extension>' \
        '    <groupId>eu.maveniverse.maven.mason</groupId>' \
        '    <artifactId>mason</artifactId>' \
        '    <version>0.3.0</version>' \
        '  </extension>' \
        '</extensions>' > "${root}/.mvn/extensions.xml"
    printf '%s\n' \
        'wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar' \
        'distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-6/apache-maven-4.0.0-rc-6-bin.zip' \
        > "${root}/.mvn/wrapper/maven-wrapper.properties"
    printf '%s\n' 'MAVEN_3_VERSION: 3.9.16' > "${root}/.github/workflows/release.yml"

    for path in "${converted_poms[@]}"; do
        mkdir -p "$(dirname "${root}/${path}")"
        printf '%s\n' 'modelVersion: 4.0.0' > "${root}/${path}"
    done

    for entry in "${xml_parent_paths[@]}"; do
        path="${entry%%:*}"
        relative_path="${entry#*:}"
        mkdir -p "$(dirname "${root}/${path}")"
        printf '<project><parent><relativePath>%s</relativePath></parent></project>\n' \
            "${relative_path}" > "${root}/${path}"
    done
}

expect_failure() {
    local expected_message="$1"
    local root="$2"
    local output

    if output="$("${verifier}" "${root}" 2>&1)"; then
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
"${verifier}" "${valid_fixture}"

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
printf '%s\n' '<project/>' > "${xml_shadow}/jfoundry-core/jfoundry-domain/pom.xml"
expect_failure "Converted project retains an XML shadow POM" "${xml_shadow}"

wrong_parent="${fixture_root}/wrong-parent"
write_valid_fixture "${wrong_parent}"
sed -i.bak 's#../../pom.yaml#../../pom.xml#' \
    "${wrong_parent}/jfoundry-core/jfoundry-application/pom.xml"
rm "${wrong_parent}/jfoundry-core/jfoundry-application/pom.xml.bak"
expect_failure "XML parent path must resolve the YAML root" "${wrong_parent}"

echo "Mason PoC verifier self-test passed."
