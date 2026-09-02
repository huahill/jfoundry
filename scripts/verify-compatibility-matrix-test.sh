#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-compatibility-matrix.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected compatibility matrix verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects_with_message() {
    local fixture="$1"
    local expected_message="$2"
    local output

    if output="$(bash "${VERIFY_SCRIPT}" "${fixture}" 2>&1)"; then
        echo "Expected compatibility matrix verification to reject ${fixture}." >&2
        exit 1
    fi
    if [[ "${output}" != *"${expected_message}"* ]]; then
        echo "Expected compatibility matrix verification to report: ${expected_message}" >&2
        echo "Actual output: ${output}" >&2
        exit 1
    fi
}

write_pom() {
    local fixture="$1"
    local artifact_id="$2"
    local property_name="$3"
    local version="$4"
    local directory="${fixture}/jfoundry-boms/${artifact_id}"

    mkdir -p "${directory}"
    cat > "${directory}/pom.xml" <<XML
<project>
  <artifactId>${artifact_id}</artifactId>
  <properties><${property_name}>${version}</${property_name}></properties>
</project>
XML
}

write_matrix() {
    local fixture="$1"

    mkdir -p "${fixture}/docs/release"
    cat > "${fixture}/docs/release/compatibility.md" <<'MARKDOWN'
# Compatibility Matrix

| Platform | Supported line | Version source |
|----------|----------------|----------------|
| Spring Boot-only | 4.1.x | `jfoundry-spring-boot-dependencies` |
| Spring Cloud | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Spring Cloud Alibaba | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Quarkus | 3.39.x | `jfoundry-quarkus-dependencies` |
| Helidon MP | 4.5.x | `jfoundry-helidon-dependencies` |
MARKDOWN
}

write_fixture() {
    local fixture="$1"

    write_pom "${fixture}" "jfoundry-spring-boot-dependencies" "spring-boot.version" "4.1.1"
    write_pom "${fixture}" "jfoundry-spring-cloud-dependencies" "spring-cloud.version" "2025.1.3"
    python3 - "${fixture}/jfoundry-boms/jfoundry-spring-cloud-dependencies/pom.xml" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
content = path.read_text()
content = content.replace("</properties>", "<spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version></properties>", 1)
path.write_text(content)
PY
    write_pom "${fixture}" "jfoundry-quarkus-dependencies" "quarkus.version" "3.39.1"
    write_pom "${fixture}" "jfoundry-helidon-dependencies" "helidon.version" "4.5.3"
    write_matrix "${fixture}"
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

matching_fixture="${temp_dir}/matching"
write_fixture "${matching_fixture}"
assert_accepts "${matching_fixture}"

patch_update_fixture="${temp_dir}/patch-update"
write_fixture "${patch_update_fixture}"
sed -i.bak 's/<helidon.version>4.5.3<\//<helidon.version>4.5.4<\//' \
    "${patch_update_fixture}/jfoundry-boms/jfoundry-helidon-dependencies/pom.xml"
rm "${patch_update_fixture}/jfoundry-boms/jfoundry-helidon-dependencies/pom.xml.bak"
assert_accepts "${patch_update_fixture}"

stale_line_fixture="${temp_dir}/stale-line"
write_fixture "${stale_line_fixture}"
sed -i.bak 's/| Quarkus | 3.39.x |/| Quarkus | 3.38.x |/' \
    "${stale_line_fixture}/docs/release/compatibility.md"
rm "${stale_line_fixture}/docs/release/compatibility.md.bak"
assert_rejects_with_message "${stale_line_fixture}" \
    "Quarkus supported line 3.38.x must match BOM version line 3.39.x"

wrong_source_fixture="${temp_dir}/wrong-source"
write_fixture "${wrong_source_fixture}"
sed -i.bak 's/| Quarkus | 3.39.x | `jfoundry-quarkus-dependencies` |/| Quarkus | 3.39.x | `other-bom` |/' \
    "${wrong_source_fixture}/docs/release/compatibility.md"
rm "${wrong_source_fixture}/docs/release/compatibility.md.bak"
assert_rejects_with_message "${wrong_source_fixture}" \
    "Quarkus version source other-bom must be jfoundry-quarkus-dependencies"

missing_platform_fixture="${temp_dir}/missing-platform"
write_fixture "${missing_platform_fixture}"
sed -i.bak '/| Spring Cloud Alibaba |/d' \
    "${missing_platform_fixture}/docs/release/compatibility.md"
rm "${missing_platform_fixture}/docs/release/compatibility.md.bak"
assert_rejects_with_message "${missing_platform_fixture}" \
    "missing platform row: Spring Cloud Alibaba"

assert_accepts "${ROOT_DIR}"

echo "Compatibility matrix verification tests passed."
