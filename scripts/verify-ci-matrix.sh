#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

require_jdk() {
    local name="$1"
    local home="$2"

    if [[ -z "${home}" ]]; then
        echo "Missing ${name}. Set ${name} to the JDK home used by the CI matrix." >&2
        exit 1
    fi

    if [[ ! -x "${home}/bin/java" ]]; then
        echo "${name} does not point to an executable JDK: ${home}" >&2
        exit 1
    fi
}

run_tests() {
    local label="$1"
    local home="$2"

    echo "==> Running CI test matrix entry: Java ${label}"
    (
        cd "${REPO_ROOT}"
        JAVA_HOME="${home}" PATH="${home}/bin:${PATH}" ./mvnw -B test
    )
}

JAVA_25_HOME="${JAVA_25_HOME:-}"

require_jdk "JAVA_25_HOME" "${JAVA_25_HOME}"

run_tests "25" "${JAVA_25_HOME}"

echo "Java 25 release-baseline test completed successfully."
