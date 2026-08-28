#!/usr/bin/env bash

set -euo pipefail

repository_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

fail() {
    echo "$1" >&2
    exit 1
}

require_file() {
    local path="$1"
    local message="$2"
    [[ -f "${repository_root}/${path}" ]] || fail "${message}: ${path}"
}

require_text() {
    local path="$1"
    local expected="$2"
    local message="$3"
    grep -Fq -- "${expected}" "${repository_root}/${path}" || fail "${message}: ${path}"
}

extension_file=".mvn/extensions.xml"
require_file "${extension_file}" "Mason extension configuration does not exist"
require_text "${extension_file}" '<groupId>eu.maveniverse.maven.mason</groupId>' \
    "Mason extension must use groupId eu.maveniverse.maven.mason"
require_text "${extension_file}" '<artifactId>mason</artifactId>' \
    "Mason extension must use artifactId mason"
require_text "${extension_file}" '<version>0.3.0</version>' \
    "Mason extension must use version 0.3.0"

converted_poms=(
    "pom.yaml"
    "jfoundry-core/jfoundry-domain/pom.yaml"
    "jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.yaml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml"
)

for yaml_pom in "${converted_poms[@]}"; do
    require_file "${yaml_pom}" "Converted Mason YAML POM does not exist"
    xml_pom="${yaml_pom%yaml}xml"
    if [[ -e "${repository_root}/${xml_pom}" ]]; then
        fail "Converted project retains an XML shadow POM: ${xml_pom}"
    fi
done

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

for entry in "${xml_parent_paths[@]}"; do
    xml_pom="${entry%%:*}"
    relative_path="${entry#*:}"
    require_file "${xml_pom}" "XML child POM does not exist"
    require_text "${xml_pom}" "<relativePath>${relative_path}</relativePath>" \
        "XML parent path must resolve the YAML root"
done

quarkus_test_modules=(
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-transaction-quarkus-runtime/pom.xml"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-domain-event-quarkus-runtime/pom.xml"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-persistence-quarkus-runtime/pom.xml"
)

for quarkus_pom in "${quarkus_test_modules[@]}"; do
    require_file "${quarkus_pom}" "Quarkus test module POM does not exist"
    require_text "${quarkus_pom}" '<artifactId>quarkus-maven-plugin</artifactId>' \
        "Quarkus test module must configure the Quarkus Maven plugin"
    require_text "${quarkus_pom}" '<extensions>true</extensions>' \
        "Quarkus test module must enable the Quarkus Maven plugin extension"
    require_text "${quarkus_pom}" '<goal>generate-code-tests</goal>' \
        "Quarkus test module must generate the serialized application model"
    require_text "${quarkus_pom}" '<skipSourceGeneration>${skipTests}</skipSourceGeneration>' \
        "Quarkus test model generation must honor skipTests"
done

require_file ".mvn/wrapper/maven-wrapper.properties" "Maven Wrapper configuration does not exist"
require_text ".mvn/wrapper/maven-wrapper.properties" "/apache-maven/4.0.0-rc-6/" \
    "Mason PoC requires Maven Wrapper 4.0.0-rc-6"
require_file ".github/workflows/release.yml" "Production release workflow does not exist"
require_text ".github/workflows/release.yml" "MAVEN_3_VERSION: 3.9.16" \
    "Production Central publication must remain on Maven 3.9.16"

echo "Mason PoC source contract verification passed."
