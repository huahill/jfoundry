#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="${script_dir}/verify-maven-4-model.sh"

if [[ ! -x "${verifier}" ]]; then
    echo "Maven 4 model verifier does not exist or is not executable: ${verifier}" >&2
    exit 1
fi

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-maven-4-model-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

write_project() {
    local path="$1"
    local model_version="$2"
    local namespace="$3"
    local reactor_tag="$4"
    local reactor_item="$5"
    local reactor_path="$6"
    mkdir -p "$(dirname "${path}")"
    cat > "${path}" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="${namespace}">
    <modelVersion>${model_version}</modelVersion>
    <groupId>example</groupId>
    <artifactId>sample</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <${reactor_tag}>
        <${reactor_item}>${reactor_path}</${reactor_item}>
    </${reactor_tag}>
</project>
XML
}

valid_root="${fixture_root}/valid/pom.xml"
write_project "${valid_root}" "4.1.0" "http://maven.apache.org/POM/4.1.0" "subprojects" "subproject" "child"
mkdir -p "${fixture_root}/valid/child"
write_project "${fixture_root}/valid/child/pom.xml" "4.1.0" "http://maven.apache.org/POM/4.1.0" "subprojects" "subproject" "nested"
mkdir -p "${fixture_root}/valid/child/nested"
cat > "${fixture_root}/valid/child/nested/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0">
    <modelVersion>4.1.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>sample</artifactId>
    <version>1.0.0</version>
</project>
XML

"${verifier}" "${fixture_root}/valid" 3

expect_failure() {
    local name="$1"
    local root="$2"
    if "${verifier}" "${root}" 3 >"${fixture_root}/${name}.out" 2>&1; then
        echo "Expected Maven 4 model verifier failure for ${name}." >&2
        exit 1
    fi
}

invalid_model="${fixture_root}/invalid-model"
mkdir -p "${invalid_model}"
write_project "${invalid_model}/pom.xml" "4.0.0" "http://maven.apache.org/POM/4.0.0" "subprojects" "subproject" ""
expect_failure "invalid-model" "${invalid_model}"

invalid_reactor="${fixture_root}/invalid-reactor"
mkdir -p "${invalid_reactor}"
write_project "${invalid_reactor}/pom.xml" "4.1.0" "http://maven.apache.org/POM/4.1.0" "modules" "module" ""
expect_failure "invalid-reactor" "${invalid_reactor}"

invalid_yaml="${fixture_root}/invalid-yaml"
mkdir -p "${invalid_yaml}"
write_project "${invalid_yaml}/pom.xml" "4.1.0" "http://maven.apache.org/POM/4.1.0" "subprojects" "subproject" ""
printf 'modelVersion: 4.1.0\n' > "${invalid_yaml}/pom.yaml"
expect_failure "invalid-yaml" "${invalid_yaml}"

echo "Maven 4.1 model verifier self-test passed."
