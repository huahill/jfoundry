#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/verify-merge-gate.sh"

readonly SUCCESS="success"
readonly SKIPPED="skipped"
readonly FAILURE="failure"
readonly REQUIRED_FULL_RESULTS=(
    "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}"
    "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${SUCCESS}"
    "${SUCCESS}" "${SUCCESS}"
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

assert_succeeds false false "${SUCCESS}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_fails false false "${FAILURE}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_succeeds false true "${SUCCESS}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_succeeds false true "${SUCCESS}" "${SKIPPED}" "${FAILURE}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" \
    "${SKIPPED}" "${SKIPPED}" "${SKIPPED}" "${SKIPPED}"
assert_succeeds true true "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${REQUIRED_FULL_RESULTS[@]}"
assert_succeeds true false "${SUCCESS}" "${SUCCESS}" "${SKIPPED}" "${REQUIRED_FULL_RESULTS[@]}"

full_results_with_skipped_native=("${REQUIRED_FULL_RESULTS[@]}")
full_results_with_skipped_native[6]="${SKIPPED}"
assert_fails true true "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${full_results_with_skipped_native[@]}"

full_results_with_failed_test=("${REQUIRED_FULL_RESULTS[@]}")
full_results_with_failed_test[0]="${FAILURE}"
assert_fails true true "${SUCCESS}" "${SUCCESS}" "${SUCCESS}" "${full_results_with_failed_test[@]}"

echo "Merge gate verification tests passed."
