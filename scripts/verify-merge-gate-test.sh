#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/verify-merge-gate.sh"

readonly SUCCESS="success"
readonly SKIPPED="skipped"
readonly FAILURE="failure"
readonly REQUIRED_CODE_RESULTS=(
    "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}"
    "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}"
    "${SUCCESS}" "${SUCCESS}" "${SUCCESS}"
)

assert_succeeds() {
    if ! verify_merge_gate "$@"; then
        echo "Expected merge gate to succeed." >&2
        exit 1
    fi
}

assert_fails() {
    if verify_merge_gate "$@" >/dev/null 2>&1; then
        echo "Expected merge gate to fail." >&2
        exit 1
    fi
}

assert_succeeds false "${SUCCESS}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_fails false "${FAILURE}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_succeeds true "${REQUIRED_CODE_RESULTS[@]}"

code_results_with_skipped_native=("${REQUIRED_CODE_RESULTS[@]}")
code_results_with_skipped_native[7]="${SKIPPED}"
assert_fails true "${code_results_with_skipped_native[@]}"

code_results_with_failed_test=("${REQUIRED_CODE_RESULTS[@]}")
code_results_with_failed_test[1]="${FAILURE}"
assert_fails true "${code_results_with_failed_test[@]}"

echo "Merge gate verification tests passed."
