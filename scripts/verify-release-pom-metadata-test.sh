#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-release-pom-metadata.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected release POM metadata verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects() {
    if bash "${VERIFY_SCRIPT}" "$1" >/dev/null 2>&1; then
        echo "Expected release POM metadata verification to reject $1." >&2
        exit 1
    fi
}

write_fixture() {
    local directory="$1"
    local version="$2"
    local root_tag="$3"
    local bom_tag="$4"

    mkdir -p "${directory}/jfoundry-boms/example-dependencies"
    cat > "${directory}/pom.xml" <<XML
<project>
  <version>${version}</version>
  <scm><tag>${root_tag}</tag></scm>
</project>
XML
    cat > "${directory}/jfoundry-boms/example-dependencies/pom.xml" <<XML
<project>
  <version>${version}</version>
  <scm><tag>${bom_tag}</tag></scm>
</project>
XML
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

stable_fixture="${temp_dir}/stable"
write_fixture "${stable_fixture}" "1.3.0" 'v${project.version}' "v1.3.0"
assert_accepts "${stable_fixture}"

stale_tag_fixture="${temp_dir}/stale-tag"
write_fixture "${stale_tag_fixture}" "1.3.0" 'v${project.version}' "v1.2.0"
assert_rejects "${stale_tag_fixture}"

mismatched_version_fixture="${temp_dir}/mismatched-version"
write_fixture "${mismatched_version_fixture}" "1.3.0" 'v${project.version}' "v1.3.0"
sed -i.bak 's/<version>1.3.0<\//<version>1.2.0<\//' \
    "${mismatched_version_fixture}/jfoundry-boms/example-dependencies/pom.xml"
rm "${mismatched_version_fixture}/jfoundry-boms/example-dependencies/pom.xml.bak"
assert_rejects "${mismatched_version_fixture}"

missing_tag_fixture="${temp_dir}/missing-tag"
write_fixture "${missing_tag_fixture}" "1.3.0" 'v${project.version}' "v1.3.0"
sed -i.bak 's#<scm><tag>v1.3.0</tag></scm>##' \
    "${missing_tag_fixture}/jfoundry-boms/example-dependencies/pom.xml"
rm "${missing_tag_fixture}/jfoundry-boms/example-dependencies/pom.xml.bak"
assert_rejects "${missing_tag_fixture}"

snapshot_fixture="${temp_dir}/snapshot"
write_fixture "${snapshot_fixture}" "1.4.0-SNAPSHOT" 'v${project.version}' "v1.3.0"
assert_accepts "${snapshot_fixture}"

stale_snapshot_tag_fixture="${temp_dir}/stale-snapshot-tag"
write_fixture "${stale_snapshot_tag_fixture}" "1.4.0-SNAPSHOT" 'v${project.version}' "v1.2.0"
assert_rejects "${stale_snapshot_tag_fixture}"

malformed_fixture="${temp_dir}/malformed"
write_fixture "${malformed_fixture}" "1.3.0" 'v${project.version}' "v1.3.0"
printf '<project>' > "${malformed_fixture}/jfoundry-boms/example-dependencies/pom.xml"
assert_rejects "${malformed_fixture}"

echo "Release POM metadata verification tests passed."
