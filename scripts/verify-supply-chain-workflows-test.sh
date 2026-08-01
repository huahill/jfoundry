#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-supply-chain-workflows.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected supply-chain workflow verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects() {
    if bash "${VERIFY_SCRIPT}" "$1" >/dev/null 2>&1; then
        echo "Expected supply-chain workflow verification to reject $1." >&2
        exit 1
    fi
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
mkdir -p "${temp_dir}/.github/workflows"

cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
  - package-ecosystem: github-actions
YAML
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    steps:
      - uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin,actions
      - uses: github/codeql-action/analyze@v4
YAML
cat > "${temp_dir}/.github/workflows/release.yml" <<'YAML'
jobs:
  publish:
    steps:
      - uses: actions/upload-artifact@v4
        with:
          path: release-evidence
YAML

assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/ci.yml" <<'YAML'
jobs:
  dependency-review:
    name: Dependency Review
    if: github.event_name == 'pull_request'
    permissions:
      contents: read
      pull-requests: read
    steps:
      - uses: actions/dependency-review-action@v4
        with:
          fail-on-severity: high
  merge-gate:
    needs:
      - dependency-review
    steps:
      - run: echo '${{ needs.dependency-review.result }}'
YAML
assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    strategy:
      matrix:
        include:
          - language: java-kotlin
            build-mode: manual
          - language: actions
            build-mode: none
    steps:
      - uses: github/codeql-action/init@v4
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
      - uses: github/codeql-action/analyze@v4
YAML
assert_accepts "${temp_dir}"
rm "${temp_dir}/.github/dependabot.yml"
assert_rejects "${temp_dir}"
assert_accepts "${ROOT_DIR}"

echo "Supply-chain workflow verification tests passed."
