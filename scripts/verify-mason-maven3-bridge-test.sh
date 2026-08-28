#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
generator="${repository_root}/scripts/generate-maven3-publication-tree.sh"
integration_verifier="${repository_root}/scripts/verify-mason-maven3-bridge.sh"

if [[ ! -x "${generator}" ]]; then
    echo "Maven 3 publication-tree generator does not exist or is not executable: ${generator}" >&2
    exit 1
fi
if [[ ! -x "${integration_verifier}" ]]; then
    echo "Maven 3 publication bridge verifier does not exist or is not executable: ${integration_verifier}" >&2
    exit 1
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-maven3-test.XXXXXX")"
trap 'rm -rf "${temporary_root}"' EXIT

inside_repository="${repository_root}/target/mason-bridge-invalid"
if "${generator}" "${inside_repository}" >/dev/null 2>&1; then
    echo "Generator accepted a destination inside the repository." >&2
    exit 1
fi

nonempty_destination="${temporary_root}/nonempty"
mkdir -p "${nonempty_destination}"
printf '%s\n' occupied > "${nonempty_destination}/marker"
if "${generator}" "${nonempty_destination}" >/dev/null 2>&1; then
    echo "Generator accepted a non-empty destination." >&2
    exit 1
fi

publication_tree="${temporary_root}/publication-tree"
"${generator}" "${publication_tree}"

while IFS= read -r yaml_pom; do
    relative_pom="${yaml_pom#${repository_root}/}"
    xml_pom="${relative_pom%yaml}xml"
    [[ -f "${publication_tree}/${xml_pom}" ]] || {
        echo "Generated XML POM does not exist: ${xml_pom}" >&2
        exit 1
    }
done < <(
    find "${repository_root}" -type f -name pom.yaml \
        -not -path '*/target/*' \
        -not -path '*/graphify-out/*' \
        -print | LC_ALL=C sort
)

if find "${publication_tree}" -name pom.yaml -print -quit | grep -q .; then
    echo "Publication tree still contains a Mason YAML POM." >&2
    exit 1
fi
if [[ -e "${publication_tree}/.mvn/extensions.xml" ]]; then
    echo "Publication tree still enables the Mason extension." >&2
    exit 1
fi
if rg -l -F "${repository_root}" "${publication_tree}" --glob 'pom.xml' | grep -q .; then
    echo "Generated XML POM contains the source workspace path." >&2
    exit 1
fi

if rg -l '<relativePath>[^<]*pom\.yaml</relativePath>' \
    "${publication_tree}" --glob 'pom.xml' | grep -q .; then
    echo "Maven 3 publication tree retains a YAML parent path." >&2
    exit 1
fi

(cd "${publication_tree}" && mvn -B -DskipTests validate)

echo "Maven 3 publication bridge self-test passed."
