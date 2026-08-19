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

cat > "${fixture_pom_dir}/jfoundry-domain-${fixture_version}.pom" <<'XML'
<project>
  <artifactId>jfoundry-domain</artifactId>
  <url>https://github.com/xfoundries/jfoundry</url>
  <licenses></licenses>
  <developers></developers>
  <scm></scm>
</project>
XML

starter_dir="${fixture_repo}/io/github/xfoundries/jfoundry-webmvc-spring-boot-starter/${fixture_version}"
mkdir -p "${starter_dir}"
cat > "${starter_dir}/jfoundry-webmvc-spring-boot-starter-${fixture_version}.pom" <<'XML'
<project>
  <artifactId>jfoundry-webmvc-spring-boot-starter</artifactId>
  <url>https://github.com/xfoundries/jfoundry</url>
  <licenses></licenses>
  <developers></developers>
  <scm></scm>
</project>
XML

for artifact in jfoundry-dependencies jfoundry-spring-boot-dependencies jfoundry-spring-cloud-dependencies; do
    artifact_dir="${fixture_repo}/io/github/xfoundries/${artifact}/${fixture_version}"
    mkdir -p "${artifact_dir}"
    cat > "${artifact_dir}/${artifact}-${fixture_version}.pom" <<XML
<project>
  <artifactId>${artifact}</artifactId>
  <url>https://github.com/xfoundries/jfoundry</url>
  <licenses></licenses>
  <developers></developers>
  <scm></scm>
  <dependencyManagement></dependencyManagement>
</project>
XML
done

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "${fixture_repo}" "${fixture_version}"; then
        echo "Expected Consumer POM verification to accept matching Spring platform lines." >&2
        exit 1
    fi
}

assert_rejects() {
    local description="$1"
    if bash "${VERIFY_SCRIPT}" "${fixture_repo}" "${fixture_version}" >/dev/null 2>&1; then
        echo "Expected Consumer POM verification to reject ${description}." >&2
        exit 1
    fi
}

assert_accepts_without_xmllint() {
    local fake_bin="${temp_dir}/without-xmllint"

    mkdir -p "${fake_bin}"
    cat > "${fake_bin}/xmllint" <<'SH'
#!/usr/bin/env bash
exit 127
SH
    chmod +x "${fake_bin}/xmllint"

    if ! PATH="${fake_bin}:${PATH}" bash "${VERIFY_SCRIPT}" "${fixture_repo}" "${fixture_version}"; then
        echo "Expected Consumer POM verification to work without xmllint." >&2
        exit 1
    fi
}

write_parent() {
    local parent_artifact="$1"
    local boot_version="$2"
    local runtime_bom="$3"
    local first_import="$4"
    local second_import="$5"
    local parent_dir="${fixture_repo}/io/github/xfoundries/${parent_artifact}/${fixture_version}"

    mkdir -p "${parent_dir}"
    cat > "${parent_dir}/${parent_artifact}-${fixture_version}.pom" <<XML
<project>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>${boot_version}</version>
  </parent>
  <artifactId>${parent_artifact}</artifactId>
  <url>https://github.com/xfoundries/jfoundry</url>
  <licenses></licenses>
  <developers></developers>
  <scm></scm>
  <properties>
    <jfoundry.version>${fixture_version}</jfoundry.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>${first_import}</artifactId>
        <version>\${jfoundry.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>${second_import}</artifactId>
        <version>\${jfoundry.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
XML
}

write_parent jfoundry-spring-boot-parent 4.1.0 jfoundry-spring-boot-dependencies jfoundry-spring-boot-dependencies jfoundry-dependencies
write_parent jfoundry-spring-cloud-parent 4.0.7 jfoundry-spring-cloud-dependencies jfoundry-spring-cloud-dependencies jfoundry-dependencies
assert_accepts

write_parent jfoundry-spring-boot-parent 4.0.7 jfoundry-spring-boot-dependencies jfoundry-spring-boot-dependencies jfoundry-dependencies
assert_rejects "a Spring Boot parent with the Cloud-line Boot version"
write_parent jfoundry-spring-boot-parent 4.1.0 jfoundry-spring-cloud-dependencies jfoundry-spring-cloud-dependencies jfoundry-dependencies
assert_rejects "a Spring Boot parent that imports the Cloud runtime BOM"
write_parent jfoundry-spring-boot-parent 4.1.0 jfoundry-spring-boot-dependencies jfoundry-dependencies jfoundry-spring-boot-dependencies
assert_rejects "a Spring Boot parent that imports the core BOM before the runtime BOM"
write_parent jfoundry-spring-boot-parent 4.1.0 jfoundry-spring-boot-dependencies jfoundry-spring-boot-dependencies jfoundry-dependencies

write_parent jfoundry-spring-cloud-parent 4.1.0 jfoundry-spring-cloud-dependencies jfoundry-spring-cloud-dependencies jfoundry-dependencies
assert_rejects "a Spring Cloud parent with the Boot-only version"
write_parent jfoundry-spring-cloud-parent 4.0.7 jfoundry-spring-boot-dependencies jfoundry-spring-boot-dependencies jfoundry-dependencies
assert_rejects "a Spring Cloud parent that imports the Boot-only runtime BOM"
write_parent jfoundry-spring-cloud-parent 4.0.7 jfoundry-spring-cloud-dependencies jfoundry-dependencies jfoundry-spring-cloud-dependencies
assert_rejects "a Spring Cloud parent that imports the core BOM before the runtime BOM"
write_parent jfoundry-spring-cloud-parent 4.0.7 jfoundry-spring-cloud-dependencies jfoundry-spring-cloud-dependencies jfoundry-dependencies
assert_accepts
assert_accepts_without_xmllint

echo "Consumer POM verification regression test passed."
