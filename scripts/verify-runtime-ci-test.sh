#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/verify-runtime-ci.sh"

declare -a CALLS=()

require_java_25() {
    :
}

require_graalvm() {
    :
}

require_docker() {
    :
}

run_maven() {
    local java_home="$1"
    shift
    CALLS+=("maven:${java_home}:$*")
}

run_maven_serially() {
    local java_home="$1"
    shift
    CALLS+=("maven-serial:${java_home}:$*")
}

spring_native_smoke_test() {
    CALLS+=("spring-native-smoke")
}

helidon_native_smoke_test() {
    CALLS+=("helidon-native-smoke")
}

is_macos() {
    return 1
}

assert_contains() {
    local expected="$1"
    local actual
    actual="$(printf '%s\n' "${CALLS[@]}")"
    if [[ "${actual}" != *"${expected}"* ]]; then
        echo "Expected call containing: ${expected}" >&2
        echo "Actual calls:" >&2
        printf '%s\n' "${CALLS[@]}" >&2
        exit 1
    fi
}

assert_not_contains() {
    local unexpected="$1"
    local actual
    actual="$(printf '%s\n' "${CALLS[@]}")"
    if [[ "${actual}" == *"${unexpected}"* ]]; then
        echo "Unexpected call containing: ${unexpected}" >&2
        printf '%s\n' "${CALLS[@]}" >&2
        exit 1
    fi
}

assert_before() {
    local first="$1"
    local second="$2"
    local actual
    actual="$(printf '%s\n' "${CALLS[@]}")"
    if [[ "${actual}" != *"${first}"*"${second}"* ]]; then
        echo "Expected ${first} before ${second}" >&2
        printf '%s\n' "${CALLS[@]}" >&2
        exit 1
    fi
}

CALLS=()
main quarkus --stage middleware
assert_contains "maven-serial::-DskipTests install"
assert_contains "-Pjvm-integration verify"
assert_not_contains "-Pnative"

CALLS=()
main spring --stage native
assert_contains "-Pnative package"
assert_contains "spring-native-smoke"

CALLS=()
main spring --stage native-mybatis-plus
assert_contains "maven::-pl ${SPRING_INTEGRATION_MODULE} -am -Pnative-mybatis-plus verify"
assert_not_contains "spring-native-smoke"

GRAALVM_HOME="/graalvm"
is_macos() {
    return 0
}
CALLS=()
main quarkus --stage native
assert_contains "maven:/graalvm:-pl ${QUARKUS_INTEGRATION_MODULE} -Pnative verify"
assert_not_contains "-Dquarkus.native.container-build=true"
is_macos() {
    return 1
}

CALLS=()
main all
assert_before "-Pit verify" "spring-native-smoke"
assert_before "spring-native-smoke" "-Pjvm-integration verify"
assert_before "spring-native-smoke" "-Pnative-mybatis-plus verify"
assert_contains "helidon-native-smoke"

if (main unknown) >/dev/null 2>&1; then
    echo "Unknown runtime must fail." >&2
    exit 1
fi

if (main spring --stage unsupported) >/dev/null 2>&1; then
    echo "Unknown stage must fail." >&2
    exit 1
fi

if (main all --stage native) >/dev/null 2>&1; then
    echo "The all runtime only supports the all stage." >&2
    exit 1
fi

echo "Runtime CI command-selection tests passed."
