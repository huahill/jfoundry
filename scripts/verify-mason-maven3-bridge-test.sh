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

converted_poms=(
    "pom.xml"
    "jfoundry-core/jfoundry-domain/pom.xml"
    "jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.xml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.xml"
)
for pom in "${converted_poms[@]}"; do
    [[ -f "${publication_tree}/${pom}" ]] || {
        echo "Generated XML POM does not exist: ${pom}" >&2
        exit 1
    }
done

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

xml_parent_paths=(
    "jfoundry-core/jfoundry-application/pom.xml:../../pom.xml"
    "jfoundry-core/jfoundry-architecture/jfoundry-architecture-test/pom.xml:../../../pom.xml"
    "jfoundry-core/jfoundry-architecture/pom.xml:../../pom.xml"
    "jfoundry-core/jfoundry-infrastructure/pom.xml:../../pom.xml"
    "jfoundry-runtime/jfoundry-helidon/pom.xml:../../pom.xml"
    "jfoundry-runtime/jfoundry-jakarta/pom.xml:../../pom.xml"
    "jfoundry-runtime/jfoundry-quarkus/pom.xml:../../pom.xml"
    "jfoundry-runtime/jfoundry-spring/pom.xml:../../pom.xml"
)
for entry in "${xml_parent_paths[@]}"; do
    pom="${entry%%:*}"
    relative_path="${entry#*:}"
    grep -Fq "<relativePath>${relative_path}</relativePath>" "${publication_tree}/${pom}" || {
        echo "Maven 3 parent path was not restored: ${pom}" >&2
        exit 1
    }
done

(cd "${publication_tree}" && mvn -B -DskipTests validate)

echo "Maven 3 publication bridge self-test passed."
