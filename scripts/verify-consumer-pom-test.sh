#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-consumer-pom.sh"

if [[ ! -x "${VERIFY_SCRIPT}" ]]; then
    echo "Consumer POM verifier does not exist or is not executable: ${VERIFY_SCRIPT}" >&2
    exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

fixture_repo="${temp_dir}/repository"
fixture_version="1.0.0"
fixture_pom_dir="${fixture_repo}/io/github/xfoundries/jfoundry-domain/${fixture_version}"
mkdir -p "${fixture_pom_dir}"

cat > "${fixture_pom_dir}/jfoundry-domain-${fixture_version}.pom" <<'XML'
<project>
  <parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-parent</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>jfoundry-domain</artifactId>
  <url>${jfoundry.release.url}</url>
</project>
XML

if bash "${VERIFY_SCRIPT}" "${fixture_repo}" "${fixture_version}" >/dev/null 2>&1; then
    echo "Expected Consumer POM verification to reject an inherited build POM." >&2
    exit 1
fi

echo "Consumer POM verification regression test passed."
