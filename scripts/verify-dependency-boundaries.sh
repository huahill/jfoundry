#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
java "${ROOT_DIR}/scripts/VerifyDependencyBoundaries.java" "${ROOT_DIR}"
