#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
generator="${repository_root}/scripts/generate-maven3-publication-tree.sh"
maven3_executable="${1:-$(command -v mvn)}"

if [[ ! -x "${maven3_executable}" ]]; then
    echo "Maven 3 executable does not exist or is not executable: ${maven3_executable}" >&2
    exit 1
fi
maven_version="$(${maven3_executable} --version | awk '/^Apache Maven / { print $3; exit }')"
if [[ "${maven_version}" != "3.9.16" ]]; then
    echo "Maven 3 publication bridge requires Maven 3.9.16, found: ${maven_version}" >&2
    exit 1
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-maven3-bridge.XXXXXX")"
publication_tree="${temporary_root}/publication-tree"
local_repository="${temporary_root}/repository"
trap 'rm -rf "${temporary_root}"' EXIT

"${generator}" "${publication_tree}"
mkdir -p "${local_repository}"

(
    cd "${publication_tree}"
    "${maven3_executable}" -B -DskipTests \
        "-DaltDeploymentRepository=local::file:${local_repository}" deploy
)

required_coordinates=(
    "jfoundry-parent"
    "jfoundry-domain"
    "jfoundry-spring-boot-starter"
    "jfoundry-helidon-dependencies"
)
for artifact_id in "${required_coordinates[@]}"; do
    artifact_directory="${local_repository}/io/github/xfoundries/${artifact_id}/1.4.0-SNAPSHOT"
    if ! find "${artifact_directory}" -maxdepth 1 -name "${artifact_id}-1.4.0-*.pom" -print -quit | grep -q .; then
        echo "Maven 3 local deployment did not produce a POM for: ${artifact_id}" >&2
        exit 1
    fi
done

echo "Maven 3 publication bridge verification passed."
