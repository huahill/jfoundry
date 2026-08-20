#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKER="${SCRIPT_DIR}/VerifyDependencyBoundaries.java"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_pom() {
    local relative_path="$1"
    local body="$2"
    local pom="${FIXTURE_ROOT}/${relative_path}/pom.xml"
    mkdir -p "$(dirname "${pom}")"
    cat >"${pom}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>fixture</groupId>
    <artifactId>fixture</artifactId>
${body}
</project>
EOF
}

expect_rejected() {
    local name="$1"
    local output
    if output="$(java "${CHECKER}" "${FIXTURE_ROOT}" 2>&1)"; then
        echo "Expected ${name} fixture to be rejected." >&2
        exit 1
    fi
    if [[ "${output}" != *"${name}"* ]]; then
        echo "${name} fixture did not appear in checker output:" >&2
        echo "${output}" >&2
        exit 1
    fi
}

expect_allowed() {
    local name="$1"
    local output
    if ! output="$(java "${CHECKER}" "${FIXTURE_ROOT}" 2>&1)"; then
        echo "Expected ${name} fixture to be allowed:" >&2
        echo "${output}" >&2
        exit 1
    fi
}

write_pom "jfoundry-core/jfoundry-infrastructure/invalid-spring" '
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot</artifactId>
        </dependency>
    </dependencies>'
expect_rejected "invalid-spring"

rm -rf "${FIXTURE_ROOT}/jfoundry-core/jfoundry-infrastructure/invalid-spring"
write_pom "jfoundry-core/jfoundry-infrastructure/invalid-spring-test" '
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>'
expect_rejected "invalid-spring-test"

rm -rf "${FIXTURE_ROOT}/jfoundry-core/jfoundry-infrastructure/invalid-spring-test"
write_pom "jfoundry-boms/jfoundry-foundation-dependencies/invalid-foundation" '
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
                <version>1.0.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>'
expect_rejected "invalid-foundation"

rm -rf "${FIXTURE_ROOT}/jfoundry-boms/jfoundry-foundation-dependencies/invalid-foundation"
write_pom "jfoundry-boms/jfoundry-foundation-dependencies/invalid-jobrunr" '
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.jobrunr</groupId>
                <artifactId>jobrunr-spring-boot-4-starter</artifactId>
                <version>1.0.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>'
expect_rejected "invalid-jobrunr"

rm -rf "${FIXTURE_ROOT}/jfoundry-boms/jfoundry-foundation-dependencies/invalid-jobrunr"
write_pom "jfoundry-runtime/jfoundry-spring/runtime/invalid-quarkus" '
    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
    </dependencies>'
expect_rejected "invalid-quarkus"

rm -rf "${FIXTURE_ROOT}/jfoundry-runtime/jfoundry-spring/runtime/invalid-quarkus"
write_pom "jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests/allowed-spring-test" '
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>'
expect_allowed "allowed-spring-test"

rm -rf "${FIXTURE_ROOT}/jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests/allowed-spring-test"
write_pom "jfoundry-core/jfoundry-infrastructure/allowed-jpa-api" '
    <dependencies>
        <dependency>
            <groupId>jakarta.persistence</groupId>
            <artifactId>jakarta.persistence-api</artifactId>
        </dependency>
    </dependencies>'
expect_allowed "allowed-jpa-api"

rm -rf "${FIXTURE_ROOT}/jfoundry-core/jfoundry-infrastructure/allowed-jpa-api"
write_pom "jfoundry-boms/jfoundry-foundation-dependencies/allowed-mybatis" '
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-core</artifactId>
                <version>1.0.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>'
expect_allowed "allowed-mybatis"

echo "Dependency boundary fixture tests passed."
