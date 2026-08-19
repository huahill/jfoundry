#!/usr/bin/env bash

set -euo pipefail

if (( $# < 2 || $# > 4 )); then
    echo "Usage: $0 <repository> <version> [maven-3-bin] [maven-4-bin]" >&2
    exit 2
fi

repository="$1"
version="$2"
maven3_bin="${3:-}"
maven4_bin="${4:-}"
group_path="io/github/xfoundries"

require_text() {
    local pom="$1"
    local text="$2"
    if ! grep -Fq -- "${text}" "${pom}"; then
        echo "Consumer POM must contain ${text}: ${pom}" >&2
        exit 1
    fi
}

forbid_text() {
    local pom="$1"
    local text="$2"
    if grep -Fq -- "${text}" "${pom}"; then
        echo "Consumer POM must not contain ${text}: ${pom}" >&2
        exit 1
    fi
}

pom_path() {
    local artifact="$1"
    printf '%s/%s/%s/%s/%s-%s.pom' \
        "${repository}" "${group_path}" "${artifact}" "${version}" "${artifact}" "${version}"
}

verify_metadata() {
    local pom="$1"
    require_text "${pom}" "<url>"
    require_text "${pom}" "<licenses>"
    require_text "${pom}" "<developers>"
    require_text "${pom}" "<scm"
}

verify_flattened_module() {
    local artifact="$1"
    local pom
    pom="$(pom_path "${artifact}")"
    if [[ ! -f "${pom}" ]]; then
        echo "Consumer POM does not exist: ${pom}" >&2
        exit 1
    fi
    verify_metadata "${pom}"
    forbid_text "${pom}" "<parent>"
    forbid_text "${pom}" '${'
}

verify_independent_bom() {
    local artifact="$1"
    local pom
    pom="$(pom_path "${artifact}")"
    if [[ ! -f "${pom}" ]]; then
        echo "BOM POM does not exist: ${pom}" >&2
        exit 1
    fi
    verify_metadata "${pom}"
    forbid_text "${pom}" "<parent>"
    require_text "${pom}" "<dependencyManagement>"
}

require_imported_bom_before() {
    local pom="$1"
    local first_artifact="$2"
    local second_artifact="$3"
    local first_index=0
    local second_index=0
    local index=0
    local artifact

    while IFS= read -r artifact; do
        ((index += 1))
        if [[ "${artifact}" == "${first_artifact}" ]]; then
            first_index="${index}"
        elif [[ "${artifact}" == "${second_artifact}" ]]; then
            second_index="${index}"
        fi
    done < <(xmllint --xpath '//*[local-name()="dependencyManagement"]/*[local-name()="dependencies"]/*[local-name()="dependency"][*[local-name()="type" and text()="pom"] and *[local-name()="scope" and text()="import"]]/*[local-name()="artifactId"]/text()' "${pom}")

    if (( first_index == 0 || second_index == 0 || first_index >= second_index )); then
        echo "Consumer POM must import ${first_artifact} before ${second_artifact}: ${pom}" >&2
        exit 1
    fi
}

verify_spring_boot_parent() {
    local pom
    pom="$(pom_path "jfoundry-spring-boot-parent")"
    if [[ ! -f "${pom}" ]]; then
        echo "Spring Boot Parent POM does not exist: ${pom}" >&2
        exit 1
    fi
    verify_metadata "${pom}"
    require_text "${pom}" "<jfoundry.version>${version}</jfoundry.version>"
    require_text "${pom}" "<artifactId>jfoundry-dependencies</artifactId>"
    require_text "${pom}" "<artifactId>jfoundry-spring-dependencies</artifactId>"
    require_text "${pom}" '<version>${jfoundry.version}</version>'
    forbid_text "${pom}" '${project.version}'
    require_imported_bom_before "${pom}" "jfoundry-spring-dependencies" "jfoundry-dependencies"
}

verify_flattened_module "jfoundry-domain"
verify_flattened_module "jfoundry-webmvc-spring-boot-starter"
verify_independent_bom "jfoundry-dependencies"
verify_independent_bom "jfoundry-spring-dependencies"
verify_spring_boot_parent

if [[ -n "${maven3_bin}" || -n "${maven4_bin}" ]]; then
    if [[ -z "${maven3_bin}" || -z "${maven4_bin}" ]]; then
        echo "Maven 3 and Maven 4 executables must be provided together." >&2
        exit 2
    fi
    for maven_bin in "${maven3_bin}" "${maven4_bin}"; do
        if [[ ! -x "${maven_bin}" ]]; then
            echo "Maven executable does not exist or is not executable: ${maven_bin}" >&2
            exit 1
        fi
    done

    temp_dir="$(mktemp -d)"
    trap 'rm -rf "${temp_dir}"' EXIT
    consumer_pom="${temp_dir}/pom.xml"
    cat > "${consumer_pom}" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.xfoundries.verification</groupId>
    <artifactId>consumer-pom-smoke</artifactId>
    <version>1.0.0</version>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.xfoundries</groupId>
                <artifactId>jfoundry-spring-dependencies</artifactId>
                <version>${version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.github.xfoundries</groupId>
                <artifactId>jfoundry-dependencies</artifactId>
                <version>${version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-webmvc-spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
XML

    spring_boot_parent_consumer_pom="${temp_dir}/spring-boot-parent-consumer-pom.xml"
    cat > "${spring_boot_parent_consumer_pom}" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-parent</artifactId>
        <version>${version}</version>
        <relativePath/>
    </parent>
    <artifactId>spring-boot-parent-consumer-smoke</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-webmvc-spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
XML

    for maven_bin in "${maven3_bin}" "${maven4_bin}"; do
        "${maven_bin}" -B -f "${consumer_pom}" -Dmaven.repo.local="${repository}" compile
        "${maven_bin}" -B -f "${spring_boot_parent_consumer_pom}" -Dmaven.repo.local="${repository}" compile
    done
fi

echo "Consumer POM verification passed: ${repository} (${version})"
