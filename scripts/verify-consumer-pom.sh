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
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

xml_query() {
    java "${script_dir}/VerifyConsumerPomXml.java" "$@"
}

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

verify_spring_cloud_bom() {
    local pom
    pom="$(pom_path "jfoundry-spring-cloud-dependencies")"
    verify_independent_bom "jfoundry-spring-cloud-dependencies"
    require_text "${pom}" "<artifactId>spring-cloud-dependencies</artifactId>"
    require_text "${pom}" "<artifactId>spring-cloud-alibaba-dependencies</artifactId>"
    forbid_text "${pom}" "<artifactId>spring-boot-dependencies</artifactId>"
    forbid_text "${pom}" "<artifactId>jfoundry-foundation-dependencies</artifactId>"
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
    done < <(xml_query imported-artifact-ids "${pom}")

    if (( first_index == 0 || second_index == 0 || first_index >= second_index )); then
        echo "Consumer POM must import ${first_artifact} before ${second_artifact}: ${pom}" >&2
        exit 1
    fi
}

require_exact_imported_boms() {
    local pom="$1"
    local first_artifact="$2"
    local second_artifact="$3"
    local actual
    local expected

    actual="$(xml_query imported-artifact-ids "${pom}")"
    expected="$(printf '%s\n%s' "${first_artifact}" "${second_artifact}")"
    if [[ "${actual}" != "${expected}" ]]; then
        echo "Consumer POM must import exactly ${first_artifact} then ${second_artifact}: ${pom}" >&2
        exit 1
    fi
}

require_parent_coordinate() {
    local pom="$1"
    local group_id="$2"
    local artifact_id="$3"
    local version="$4"
    local actual

    actual="$(xml_query parent-coordinate "${pom}")"
    if [[ "${actual}" != "${group_id}:${artifact_id}:${version}" ]]; then
        echo "Consumer POM must inherit ${group_id}:${artifact_id}:${version}: ${pom}" >&2
        exit 1
    fi
}

verify_spring_parent() {
    local artifact="$1"
    local boot_version="$2"
    local runtime_bom="$3"
    local pom
    pom="$(pom_path "${artifact}")"
    if [[ ! -f "${pom}" ]]; then
        echo "Spring parent POM does not exist: ${pom}" >&2
        exit 1
    fi
    verify_metadata "${pom}"
    require_text "${pom}" "<jfoundry.version>${version}</jfoundry.version>"
    require_text "${pom}" "<artifactId>jfoundry-dependencies</artifactId>"
    require_text "${pom}" "<artifactId>${runtime_bom}</artifactId>"
    require_text "${pom}" '<version>${jfoundry.version}</version>'
    forbid_text "${pom}" '${project.version}'
    forbid_text "${pom}" "jfoundry-spring-dependencies"
    require_parent_coordinate "${pom}" "org.springframework.boot" "spring-boot-starter-parent" "${boot_version}"
    require_imported_bom_before "${pom}" "${runtime_bom}" "jfoundry-dependencies"
    require_exact_imported_boms "${pom}" "${runtime_bom}" "jfoundry-dependencies"
}

verify_flattened_module "jfoundry-domain"
verify_flattened_module "jfoundry-webmvc-spring-boot-starter"
verify_independent_bom "jfoundry-dependencies"
verify_independent_bom "jfoundry-spring-boot-dependencies"
verify_spring_cloud_bom
spring_boot_bom_pom="$(pom_path "jfoundry-spring-boot-dependencies")"
spring_boot_version="$(xml_query property-value "${spring_boot_bom_pom}" "spring-boot.version")"
if [[ -z "${spring_boot_version}" ]]; then
    echo "Consumer POM must define spring-boot.version: ${spring_boot_bom_pom}" >&2
    exit 1
fi
verify_spring_parent "jfoundry-spring-boot-parent" "${spring_boot_version}" "jfoundry-spring-boot-dependencies"

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
    boot_consumer_pom="${temp_dir}/boot-consumer-pom.xml"
    cat > "${boot_consumer_pom}" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.xfoundries.verification</groupId>
    <artifactId>boot-consumer-pom-smoke</artifactId>
    <version>1.0.0</version>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.xfoundries</groupId>
                <artifactId>jfoundry-spring-boot-dependencies</artifactId>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
XML

    cloud_consumer_pom="${temp_dir}/cloud-consumer-pom.xml"
    cat > "${cloud_consumer_pom}" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.7</version>
        <relativePath/>
    </parent>
    <groupId>io.github.xfoundries.verification</groupId>
    <artifactId>cloud-consumer-pom-smoke</artifactId>
    <version>1.0.0</version>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.xfoundries</groupId>
                <artifactId>jfoundry-spring-cloud-dependencies</artifactId>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
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
        "${maven_bin}" -B -f "${boot_consumer_pom}" -Dmaven.repo.local="${repository}" compile
        "${maven_bin}" -B -f "${cloud_consumer_pom}" -Dmaven.repo.local="${repository}" compile
        "${maven_bin}" -B -f "${spring_boot_parent_consumer_pom}" -Dmaven.repo.local="${repository}" compile
    done
fi

echo "Consumer POM verification passed: ${repository} (${version})"
