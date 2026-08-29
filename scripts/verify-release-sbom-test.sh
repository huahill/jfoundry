#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-release-sbom.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected SBOM verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects() {
    if bash "${VERIFY_SCRIPT}" "$1" >/dev/null 2>&1; then
        echo "Expected SBOM verification to reject $1." >&2
        exit 1
    fi
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

safe_pom="${temp_dir}/safe-pom.xml"
cat > "${safe_pom}" <<'XML'
<project>
  <profiles>
    <profile>
      <id>release</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.cyclonedx</groupId>
            <artifactId>cyclonedx-maven-plugin</artifactId>
            <executions>
              <execution>
                <phase>package</phase>
                <goals><goal>makeAggregateBom</goal></goals>
              </execution>
            </executions>
            <configuration>
              <outputFormat>all</outputFormat>
              <includeTestScope>false</includeTestScope>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
XML

unsafe_pom="${temp_dir}/unsafe-pom.xml"
cat > "${unsafe_pom}" <<'XML'
<project><build><plugins><plugin><artifactId>cyclonedx-maven-plugin</artifactId></plugin></plugins></build></project>
XML

assert_rejects "${unsafe_pom}"
assert_accepts "${safe_pom}"
assert_accepts "${ROOT_DIR}/pom.yaml"

yaml_test_scope="${temp_dir}/yaml-pom.yaml"
sed 's/includeTestScope: "false"/includeTestScope: false/' "${ROOT_DIR}/pom.yaml" > "${yaml_test_scope}"
assert_accepts "${yaml_test_scope}"

echo "Release SBOM verification tests passed."
