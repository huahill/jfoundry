#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verify_script="${root_dir}/scripts/verify-workflow-paths.sh"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
mkdir -p "${temp_dir}/.github/workflows"
cp "${root_dir}/.github/workflows/ci.yml" "${temp_dir}/.github/workflows/ci.yml"
cp "${root_dir}/.github/workflows/codeql.yml" "${temp_dir}/.github/workflows/codeql.yml"
cp "${root_dir}/.github/workflows/snapshot.yml" "${temp_dir}/.github/workflows/snapshot.yml"

bash "${verify_script}" "${temp_dir}"
python3 - "${temp_dir}/.github/workflows/ci.yml" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
content = path.read_text()
old = "README.md|README_ZH.md|AGENTS.md|docs/*)"
if old not in content:
    raise SystemExit("Expected CI whitelist marker")
path.write_text(content.replace(old, "README.md|README_ZH.md|docs/*)", 1))
PY
if bash "${verify_script}" "${temp_dir}" >/dev/null 2>&1; then
    echo "Expected workflow path verification to reject a CI whitelist without AGENTS.md." >&2
    exit 1
fi

echo "Workflow path verification tests passed."
