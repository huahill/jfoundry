#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
resolver="${script_dir}/maven-project-version.sh"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

wrapper_version="$(
    sed -n 's#.*apache-maven-\([^/]*\)-bin\.zip#\1#p' \
        "${repo_root}/.mvn/wrapper/maven-wrapper.properties"
)"
fake_maven="${temp_dir}/mvnw"
cat > "${fake_maven}" <<'EOF'
#!/bin/sh
cat "${FAKE_MAVEN_STDOUT_FILE}"
EOF
chmod +x "${fake_maven}"

expect_version() {
    local name="$1"
    local output="$2"
    local expected="$3"
    local actual

    printf '%s' "${output}" > "${temp_dir}/stdout"
    actual="$(
        FAKE_MAVEN_STDOUT_FILE="${temp_dir}/stdout" \
            MAVEN_PROJECT_VERSION_EXECUTABLE="${fake_maven}" \
            bash "${resolver}"
    )"
    if [[ "${actual}" != "${expected}" ]]; then
        echo "${name}: expected ${expected}, got ${actual}" >&2
        exit 1
    fi
}

expect_failure() {
    local name="$1"
    local output="$2"

    printf '%s' "${output}" > "${temp_dir}/stdout"
    if FAKE_MAVEN_STDOUT_FILE="${temp_dir}/stdout" \
        MAVEN_PROJECT_VERSION_EXECUTABLE="${fake_maven}" \
        bash "${resolver}" >/dev/null 2>&1; then
        echo "${name}: expected failure" >&2
        exit 1
    fi
}

expect_version "distribution version before project version" "${wrapper_version}"$'\n1.2.3-SNAPSHOT\n' "1.2.3-SNAPSHOT"
expect_version "legacy decorated stdout" $'[INFO] [stdout] 1.2.3\n' "1.2.3"
expect_version "plain project version" $'1.2.3\n' "1.2.3"
expect_failure "distribution version only" "${wrapper_version}"$'\n'

echo "Maven project version resolution tests passed."
