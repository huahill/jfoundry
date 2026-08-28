#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
converter="${repository_root}/scripts/support/convert-xml-pom-to-mason-yaml.rb"
model_writer="${repository_root}/scripts/support/ConvertMasonPom.java"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-converter-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT
mkdir -p "${fixture_root}/xml" "${fixture_root}/yaml"

cat > "${fixture_root}/xml/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         child.project.url.inherit.append.path="false">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>example</groupId>
        <artifactId>fixture-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <groupId>example</groupId>
    <artifactId>converter-fixture</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <properties>
        <argLine/>
        <java.version>25</java.version>
    </properties>
    <dependencies>
        <!-- Keep the dependency comment. -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.14.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <configuration>
                    <rules>
                        <requireReleaseVersion/>
                    </rules>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>example</groupId>
                            <artifactId>processor</artifactId>
                            <version>1.0</version>
                        </path>
                    </annotationProcessorPaths>
                    <transformers>
                        <transformer>
                            <mainClass>example.Main</mainClass>
                        </transformer>
                    </transformers>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
XML

ruby "${converter}" "${fixture_root}/xml/pom.xml" "${fixture_root}/yaml/pom.yaml"

grep -Fq '# Keep the dependency comment.' "${fixture_root}/yaml/pom.yaml"
grep -Fq 'argLine: ""' "${fixture_root}/yaml/pom.yaml"
grep -Fq 'requireReleaseVersion: {}' "${fixture_root}/yaml/pom.yaml"
grep -Fq 'annotationProcessorPaths:' "${fixture_root}/yaml/pom.yaml"
grep -Fq '          path:' "${fixture_root}/yaml/pom.yaml"
grep -Fq '            groupId: example' "${fixture_root}/yaml/pom.yaml"
grep -Fq 'child.project.url.inherit.append.path: "false"' "${fixture_root}/yaml/pom.yaml"
grep -Fq 'relativePath: ../pom.yaml' "${fixture_root}/yaml/pom.yaml"

maven_home_path="$(
    "${repository_root}/mvnw" --version |
        sed -n 's/^Maven home: //p' |
        head -n 1
)"
local_repository="${MAVEN_USER_HOME:-${HOME}/.m2}/repository"
classpath_entries=(
    "${maven_home_path}/lib/*" \
    "${local_repository}/eu/maveniverse/maven/mason/mason/0.3.0/mason-0.3.0.jar" \
    "${local_repository}/com/fasterxml/jackson/core/jackson-databind/2.21.1/jackson-databind-2.21.1.jar" \
    "${local_repository}/com/fasterxml/jackson/core/jackson-core/2.21.1/jackson-core-2.21.1.jar" \
    "${local_repository}/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.21.1/jackson-dataformat-yaml-2.21.1.jar" \
    "${local_repository}/com/fasterxml/jackson/dataformat/jackson-dataformat-toml/2.21.1/jackson-dataformat-toml-2.21.1.jar" \
    "${local_repository}/org/yaml/snakeyaml/2.5/snakeyaml-2.5.jar" \
    "${local_repository}/com/typesafe/config/1.4.7/config-1.4.7.jar"
)
converter_classpath="$(IFS=:; echo "${classpath_entries[*]}")"

java --class-path "${converter_classpath}" "${model_writer}" \
    "${fixture_root}/yaml/pom.yaml" "${fixture_root}/roundtrip.xml"
xmllint --noout "${fixture_root}/roundtrip.xml"

xpath() {
    xmllint --xpath "$1" "${fixture_root}/roundtrip.xml"
}

assert_equals() {
    local expected="$1"
    local actual="$2"
    local description="$3"

    if [[ "${actual}" != "${expected}" ]]; then
        echo "Assertion failed for ${description}: expected '${expected}', got '${actual}'" >&2
        exit 1
    fi
}

assert_equals "" "$(xpath 'string(/*[local-name()="project"]/*[local-name()="properties"]/*[local-name()="argLine"])')" "empty property"
assert_equals "1" "$(xpath 'count(/*[local-name()="project"]/*[local-name()="dependencies"]/*[local-name()="dependency"])')" "dependency"
assert_equals "1" "$(xpath 'count(//*[local-name()="requireReleaseVersion"])')" "empty configuration element"
assert_equals "1" "$(xpath 'count(//*[local-name()="annotationProcessorPaths"]/*[local-name()="path"])')" "annotation processor path"
assert_equals "0" "$(xpath 'count(//*[local-name()="annotationProcessorPaths"]/*[local-name()="annotationProcessorPath"])')" "incorrect inferred annotation processor path"
assert_equals "false" "$(xpath 'string(/*[local-name()="project"]/@child.project.url.inherit.append.path)')" "project inheritance attribute"
assert_equals "../pom.yaml" "$(xpath 'string(/*[local-name()="project"]/*[local-name()="parent"]/*[local-name()="relativePath"])')" "parent relative path"

echo "Mason XML-to-YAML converter test passed."
