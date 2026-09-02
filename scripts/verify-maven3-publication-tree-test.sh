#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
generator="${repository_root}/scripts/generate-maven3-publication-tree.sh"
maven3_bin="${1:-$(command -v mvn)}"

if [[ ! -x "${maven3_bin}" ]]; then
    echo "Maven 3 executable does not exist or is not executable: ${maven3_bin}" >&2
    exit 1
fi

if grep -Fq 'ru'"by" "${generator}"; then
    echo "Publication-tree generator must not depend on the legacy interpreter." >&2
    exit 1
fi

maven3_version="$("${maven3_bin}" --version | awk '/^Apache Maven / { print $3; exit }')"
if [[ "${maven3_version}" != "3.9.16" ]]; then
    echo "Expected Maven 3.9.16, found: ${maven3_version}" >&2
    exit 1
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-maven3-publication-tree.XXXXXX")"
trap 'rm -rf "${temporary_root}"' EXIT

if "${generator}" "${repository_root}/target/invalid-publication-tree" >/dev/null 2>&1; then
    echo "Generator accepted a destination inside the repository." >&2
    exit 1
fi

nonempty_destination="${temporary_root}/nonempty"
mkdir -p "${nonempty_destination}"
touch "${nonempty_destination}/marker"
if "${generator}" "${nonempty_destination}" >/dev/null 2>&1; then
    echo "Generator accepted a non-empty destination." >&2
    exit 1
fi

publication_tree="${temporary_root}/publication-tree"
local_repository="${temporary_root}/repository"
"${generator}" "${publication_tree}"

if rg -l -F '<modelVersion>4.1.0</modelVersion>' "${publication_tree}" --glob 'pom.xml' | grep -q .; then
    echo "Publication tree still contains Maven 4 modelVersion elements." >&2
    exit 1
fi
if rg -l -F '<subprojects>' "${publication_tree}" --glob 'pom.xml' | grep -q .; then
    echo "Publication tree still contains Maven 4 subprojects elements." >&2
    exit 1
fi
if rg -l -e 'child\.[A-Za-z0-9_.-]+=' "${publication_tree}" --glob 'pom.xml' | grep -q .; then
    echo "Publication tree still contains Maven 4 child.* attributes." >&2
    exit 1
fi
rg -Uq '<modelVersion>\s*4\.0\.0\s*</modelVersion>' "${publication_tree}/pom.xml"
rg -Uq '<modules>\s*<module>' "${publication_tree}/pom.xml"

mkdir -p "${local_repository}"
(
    cd "${publication_tree}"
    "${maven3_bin}" -B -DskipTests -Denforcer.skip=true validate
)

echo "Maven 3 publication-tree verification passed."
