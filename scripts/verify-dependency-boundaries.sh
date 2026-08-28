#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ruby --disable-gems "${ROOT_DIR}/scripts/VerifyDependencyBoundaries.rb" "${1:-${ROOT_DIR}}"
