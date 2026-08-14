#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MVNW="${REPO_ROOT}/mvnw"

SPRING_INTEGRATION_MODULE="jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests"
QUARKUS_INTEGRATION_MODULE="jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests"
HELIDON_INTEGRATION_MODULE="jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests"

JAVA_25_HOME="${JAVA_25_HOME:-}"
GRAALVM_HOME="${GRAALVM_HOME:-}"

usage() {
    cat <<'EOF'
Usage: scripts/verify-runtime-ci.sh <spring|quarkus|helidon|all> [--stage <middleware|native|native-mybatis-plus|native-redisson|native-jobrunr|all>]

Runs the selected runtime's CI-equivalent verification. The default stage is all.

Environment:
  JAVA_25_HOME  Java 25 JDK used for JVM verification.
  GRAALVM_HOME  GraalVM with Native Image, required for Spring and Helidon native verification,
                 and Quarkus native verification on macOS.

Docker is required for all middleware verification, Spring MyBatis-Plus Native verification,
and Quarkus native verification.
EOF
}

fail() {
    echo "$*" >&2
    exit 64
}

require_java_25() {
    if [[ -z "${JAVA_25_HOME}" || ! -x "${JAVA_25_HOME}/bin/java" ]]; then
        echo "Missing Java 25. Set JAVA_25_HOME to a Java 25 JDK home." >&2
        exit 1
    fi
}

require_graalvm() {
    if [[ -z "${GRAALVM_HOME}" || ! -x "${GRAALVM_HOME}/bin/native-image" ]]; then
        echo "Missing GraalVM Native Image. Set GRAALVM_HOME to a GraalVM home with native-image." >&2
        exit 1
    fi
}

require_docker() {
    if ! docker info >/dev/null 2>&1; then
        echo "Docker must be running for this verification." >&2
        exit 1
    fi
}

is_macos() {
    [[ "$(uname -s)" == "Darwin" ]]
}

run_maven() {
    local java_home="$1"
    shift
    (
        cd "${REPO_ROOT}"
        JAVA_HOME="${java_home}" PATH="${java_home}/bin:${PATH}" "${MVNW}" -B "$@"
    )
}

run_maven_serially() {
    local java_home="$1"
    shift
    (
        cd "${REPO_ROOT}"
        JAVA_HOME="${java_home}" PATH="${java_home}/bin:${PATH}" "${MVNW}" -B -T 1 "$@"
    )
}

spring_native_smoke_test() {
    local application="${REPO_ROOT}/${SPRING_INTEGRATION_MODULE}/target/jfoundry-spring-integration-tests"
    local log_file="/tmp/jfoundry-spring-native.log"
    local application_pid

    "${application}" --server.port=18081 >"${log_file}" 2>&1 &
    application_pid=$!

    for attempt in $(seq 1 30); do
        if curl --fail --silent --show-error http://127.0.0.1:18081/jfoundry/native/ready | grep -qx 'ready'; then
            kill "${application_pid}" 2>/dev/null || true
            wait "${application_pid}" 2>/dev/null || true
            return 0
        fi
        sleep 1
    done

    cat "${log_file}"
    kill "${application_pid}" 2>/dev/null || true
    wait "${application_pid}" 2>/dev/null || true
    return 1
}

helidon_native_smoke_test() {
    local application="${REPO_ROOT}/${HELIDON_INTEGRATION_MODULE}/target/jfoundry-helidon-integration-tests"
    local log_file="/tmp/jfoundry-helidon-native.log"
    local headers_file="/tmp/jfoundry-helidon-problem.headers"
    local body_file="/tmp/jfoundry-helidon-problem.body"
    local application_pid

    "${application}" >"${log_file}" 2>&1 &
    application_pid=$!

    for attempt in $(seq 1 30); do
        if curl -sS -D "${headers_file}" -o "${body_file}" http://127.0.0.1:7001/jfoundry/problems; then
            if grep -q '^HTTP/1.1 400' "${headers_file}" \
                && grep -qi '^Content-Type: application/problem+json' "${headers_file}" \
                && grep -q '"status":400' "${body_file}"; then
                kill "${application_pid}" 2>/dev/null || true
                wait "${application_pid}" 2>/dev/null || true
                return 0
            fi
        fi
        sleep 1
    done

    cat "${log_file}"
    kill "${application_pid}" 2>/dev/null || true
    wait "${application_pid}" 2>/dev/null || true
    return 1
}

verify_spring() {
    local stage="$1"

    if [[ "${stage}" == "middleware" || "${stage}" == "all" ]]; then
        require_java_25
        require_docker
        run_maven "${JAVA_25_HOME}" -pl "${SPRING_INTEGRATION_MODULE}" -am -Pit verify
    fi

    if [[ "${stage}" == "native" || "${stage}" == "all" ]]; then
        require_graalvm
        run_maven "${GRAALVM_HOME}" -pl "${SPRING_INTEGRATION_MODULE}" -am -Pnative package
        spring_native_smoke_test
    fi

    if [[ "${stage}" == "native-mybatis-plus" || "${stage}" == "all" ]]; then
        require_graalvm
        require_docker
        run_maven "${GRAALVM_HOME}" -pl "${SPRING_INTEGRATION_MODULE}" -am -Pnative-mybatis-plus verify
    fi

    if [[ "${stage}" == "native-redisson" || "${stage}" == "all" ]]; then
        require_graalvm
        require_docker
        run_maven "${GRAALVM_HOME}" -pl "${SPRING_INTEGRATION_MODULE}" -am -Pnative-redisson verify
    fi

    if [[ "${stage}" == "native-jobrunr" || "${stage}" == "all" ]]; then
        require_graalvm
        require_docker
        run_maven "${GRAALVM_HOME}" -pl "${SPRING_INTEGRATION_MODULE}" -am -Pnative-jobrunr clean verify
    fi
}

verify_quarkus() {
    local stage="$1"

    require_java_25
    require_docker
    run_maven_serially "${JAVA_25_HOME}" -DskipTests install

    if [[ "${stage}" == "middleware" || "${stage}" == "all" ]]; then
        run_maven "${JAVA_25_HOME}" -pl "${QUARKUS_INTEGRATION_MODULE}" -am -Pjvm-integration verify
    fi

    if [[ "${stage}" == "native" || "${stage}" == "all" ]]; then
        if is_macos; then
            require_graalvm
            run_maven "${GRAALVM_HOME}" -pl "${QUARKUS_INTEGRATION_MODULE}" -Pnative verify
        else
            run_maven "${JAVA_25_HOME}" -pl "${QUARKUS_INTEGRATION_MODULE}" -Pnative -Dquarkus.native.container-build=true verify
        fi
    fi
}

verify_helidon() {
    local stage="$1"

    if [[ "${stage}" == "middleware" || "${stage}" == "all" ]]; then
        require_java_25
        require_docker
        run_maven "${JAVA_25_HOME}" -pl "${HELIDON_INTEGRATION_MODULE}" -am -Pjvm-integration verify
    fi

    if [[ "${stage}" == "native" || "${stage}" == "all" ]]; then
        require_graalvm
        run_maven "${GRAALVM_HOME}" -pl "${HELIDON_INTEGRATION_MODULE}" -am -Pnative-image package
        helidon_native_smoke_test
    fi
}

main() {
    if [[ $# -eq 0 || "$1" == "--help" || "$1" == "-h" ]]; then
        usage
        [[ $# -gt 0 ]] && return 0
        return 64
    fi

    local runtime="$1"
    local stage="all"
    shift

    if [[ $# -gt 0 ]]; then
        [[ $# -eq 2 && "$1" == "--stage" ]] || fail "Expected --stage <middleware|native|native-mybatis-plus|native-redisson|native-jobrunr|all>."
        stage="$2"
    fi

    case "${stage}" in
        middleware|native|native-mybatis-plus|native-redisson|native-jobrunr|all) ;;
        *) fail "Unknown stage: ${stage}" ;;
    esac

    case "${runtime}" in
        spring)
            verify_spring "${stage}"
            ;;
        quarkus)
            verify_quarkus "${stage}"
            ;;
        helidon)
            verify_helidon "${stage}"
            ;;
        all)
            [[ "${stage}" == "all" ]] || fail "The all runtime only supports the all stage."
            verify_spring all
            verify_quarkus all
            verify_helidon all
            ;;
        *)
            fail "Unknown runtime: ${runtime}"
            ;;
    esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
