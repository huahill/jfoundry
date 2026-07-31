#!/usr/bin/env bash

set -euo pipefail

verify_merge_gate() {
    if [[ "$#" -ne 14 ]]; then
        echo "Expected run_full plus 13 job results, received $# arguments." >&2
        return 2
    fi

    local run_full="$1"
    shift

    local -a job_names=(
        "Documentation checks"
        "Test"
        "Package artifacts"
        "Spring middleware integration"
        "Quarkus middleware integration"
        "Helidon middleware integration"
        "Spring Native Image"
        "Spring Native Image (MyBatis-Plus)"
        "Spring Native Image (Redisson)"
        "Spring Native Image (JobRunr)"
        "Quarkus Native Image"
        "Helidon Native Image"
        "Maven 4 compatibility"
    )
    local -a job_results=("$@")
    local index

    if [[ "${run_full}" != "true" && "${run_full}" != "false" ]]; then
        echo "run_full must be true or false, received: ${run_full}" >&2
        return 2
    fi

    if [[ "${job_results[0]}" != "success" ]]; then
        echo "Documentation checks must succeed, received: ${job_results[0]}" >&2
        return 1
    fi

    if [[ "${run_full}" == "false" ]]; then
        return 0
    fi

    for index in "${!job_names[@]}"; do
        if [[ "${job_results[index]}" != "success" ]]; then
            echo "${job_names[index]} must succeed for code changes, received: ${job_results[index]}" >&2
            return 1
        fi
    done
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    verify_merge_gate "$@"
fi
