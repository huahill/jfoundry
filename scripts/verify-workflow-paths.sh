#!/usr/bin/env bash

set -euo pipefail

root_dir="${1:-.}"

require_text() {
    local file="$1"
    local text="$2"
    if ! grep -Fq -- "${text}" "${root_dir}/${file}"; then
        echo "${file} must contain: ${text}" >&2
        exit 1
    fi
}

for workflow in .github/workflows/codeql.yml .github/workflows/snapshot.yml; do
    if [[ ! -f "${root_dir}/${workflow}" ]]; then
        echo "Workflow does not exist: ${workflow}" >&2
        exit 1
    fi
done

require_text ".github/workflows/ci.yml" "README.md|README_ZH.md|AGENTS.md|docs/*)"

python3 - "${root_dir}/.github/workflows/codeql.yml" "${root_dir}/.github/workflows/snapshot.yml" <<'PY'
import sys
from pathlib import Path
import yaml

expected = ["README.md", "README_ZH.md", "AGENTS.md", "docs/**"]
for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    workflow = yaml.safe_load(path.read_text(encoding="utf-8"))
    events = workflow.get(True, workflow.get("on"))
    if events["push"]["paths-ignore"] != expected:
        raise SystemExit(f"{path} push paths-ignore must be {expected!r}")
    if path.name == "codeql.yml" and events["pull_request"]["paths-ignore"] != expected:
        raise SystemExit(f"{path} pull_request paths-ignore must be {expected!r}")
PY

echo "Workflow path verification passed: documentation whitelist is README.md, README_ZH.md, AGENTS.md, docs/**"
