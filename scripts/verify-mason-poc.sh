#!/usr/bin/env bash

set -euo pipefail

repository_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
expected_yaml_count="${2:-122}"

fail() {
    echo "$1" >&2
    exit 1
}

require_file() {
    local path="$1"
    local message="$2"
    [[ -f "${repository_root}/${path}" ]] || fail "${message}: ${path}"
}

require_text() {
    local path="$1"
    local expected="$2"
    local message="$3"
    grep -Fq -- "${expected}" "${repository_root}/${path}" || fail "${message}: ${path}"
}

extension_file=".mvn/extensions.xml"
require_file "${extension_file}" "Mason extension configuration does not exist"
require_text "${extension_file}" '<groupId>eu.maveniverse.maven.mason</groupId>' \
    "Mason extension must use groupId eu.maveniverse.maven.mason"
require_text "${extension_file}" '<artifactId>mason</artifactId>' \
    "Mason extension must use artifactId mason"
require_text "${extension_file}" '<version>0.3.0</version>' \
    "Mason extension must use version 0.3.0"

require_file "pom.yaml" "Mason YAML root POM does not exist"

fixture_pom="jfoundry-boms/jfoundry-spring-boot-parent/src/test/resources/single-parent-consumer/pom.xml"
require_file "${fixture_pom}" "XML consumer fixture POM does not exist"
if [[ -e "${repository_root}/${fixture_pom%xml}yaml" ]]; then
    fail "XML consumer fixture must remain XML: ${fixture_pom}"
fi

yaml_count=0
while IFS= read -r yaml_pom; do
    relative_yaml="${yaml_pom#${repository_root}/}"
    xml_pom="${yaml_pom%yaml}xml"
    yaml_count=$((yaml_count + 1))
    require_text "${relative_yaml}" 'modelVersion: 4.1.0' \
        "Mason YAML POM must use the Maven 4.1.0 model"
    if [[ -e "${xml_pom}" ]]; then
        fail "Converted project retains an XML shadow POM: ${relative_yaml%yaml}xml"
    fi
    if grep -Eq 'relativePath:[[:space:]]+[^#]*pom\.xml' "${yaml_pom}"; then
        fail "YAML parent path must resolve a YAML POM: ${relative_yaml}"
    fi
    if grep -Eq '^[[:space:]]*"?@[^:]*"?:' "${yaml_pom}"; then
        fail "Mason 0.3.0 does not support plugin configuration attributes: ${relative_yaml}"
    fi
done < <(
    find "${repository_root}" -type f -name pom.yaml \
        -not -path '*/target/*' \
        -not -path '*/graphify-out/*' \
        -print | LC_ALL=C sort
)
[[ "${yaml_count}" -gt 0 ]] || fail "No Mason YAML reactor POMs were found"
[[ "${yaml_count}" -eq "${expected_yaml_count}" ]] || \
    fail "Expected ${expected_yaml_count} Mason YAML reactor POMs, found ${yaml_count}"

reachable_yaml_count="$(ruby - "${repository_root}" <<'RUBY'
require "pathname"
require "yaml"

root = Pathname(ARGV.fetch(0)).realpath
seen = {}
active = {}

visit = lambda do |pom|
  unless pom.file?
    warn "Reactor subproject does not contain pom.yaml: #{pom.relative_path_from(root)}"
    exit 1
  end

  pom = pom.realpath
  unless pom.to_s.start_with?("#{root}#{File::SEPARATOR}")
    warn "Reactor subproject resolves outside repository root: #{pom}"
    exit 1
  end
  if active[pom]
    warn "Reactor subproject cycle detected: #{pom.relative_path_from(root)}"
    exit 1
  end
  if seen[pom]
    warn "Reactor subproject is declared more than once: #{pom.relative_path_from(root)}"
    exit 1
  end

  seen[pom] = true
  active[pom] = true
  model = YAML.safe_load(pom.read, aliases: true)
  unless model.is_a?(Hash)
    warn "Mason YAML POM must contain a project mapping: #{pom.relative_path_from(root)}"
    exit 1
  end

  Array(model["subprojects"]).each do |subproject_path|
    unless subproject_path.is_a?(String) && !subproject_path.empty?
      warn "Reactor subproject path must be a non-empty string: #{pom.relative_path_from(root)}"
      exit 1
    end

    subproject_path = Pathname(subproject_path)
    if subproject_path.absolute?
      warn "Reactor subproject path must be relative: #{subproject_path}"
      exit 1
    end
    if subproject_path.each_filename.include?("..")
      warn "Reactor subproject path must not contain '..': #{subproject_path}"
      exit 1
    end

    child = pom.dirname.join(subproject_path)
    child = child.join("pom.yaml") unless child.extname == ".yaml"
    visit.call(child.cleanpath)
  end

  active.delete(pom)
end

visit.call(root.join("pom.yaml"))

all_yaml_poms = root.glob("**/pom.yaml").reject do |pom|
  parts = pom.relative_path_from(root).each_filename.to_a
  parts.include?("target") || parts.include?("graphify-out")
end.map(&:realpath)

(all_yaml_poms - seen.keys).sort.each do |pom|
  warn "Mason YAML POM is not reachable from the root reactor: #{pom.relative_path_from(root)}"
  exit 1
end

puts seen.length
RUBY
)"
[[ "${reachable_yaml_count}" -eq "${expected_yaml_count}" ]] || \
    fail "Expected ${expected_yaml_count} reachable Mason YAML reactor POMs, found ${reachable_yaml_count}"

while IFS= read -r xml_pom; do
    relative_xml="${xml_pom#${repository_root}/}"
    [[ "${relative_xml}" == "${fixture_pom}" ]] && continue
    if [[ -e "${xml_pom%xml}yaml" ]]; then
        fail "Converted project retains an XML shadow POM: ${relative_xml}"
    fi
    fail "Reactor POM must use Mason YAML: ${relative_xml}"
done < <(
    find "${repository_root}" -type f -name pom.xml \
        -not -path '*/target/*' \
        -not -path '*/graphify-out/*' \
        -print | LC_ALL=C sort
)

quarkus_test_modules=(
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-transaction-quarkus-runtime/pom.yaml"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-domain-event-quarkus-runtime/pom.yaml"
    "jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-persistence-quarkus-runtime/pom.yaml"
)

for quarkus_pom in "${quarkus_test_modules[@]}"; do
    require_file "${quarkus_pom}" "Quarkus test module POM does not exist"
    require_text "${quarkus_pom}" 'artifactId: quarkus-maven-plugin' \
        "Quarkus test module must configure the Quarkus Maven plugin"
    if ! grep -Eq 'extensions:[[:space:]]+"?true"?' "${repository_root}/${quarkus_pom}"; then
        fail "Quarkus test module must enable the Quarkus Maven plugin extension: ${quarkus_pom}"
    fi
    require_text "${quarkus_pom}" 'generate-code-tests' \
        "Quarkus test module must generate the serialized application model"
    require_text "${quarkus_pom}" 'skipSourceGeneration: ${skipTests}' \
        "Quarkus test model generation must honor skipTests"
done

require_file ".mvn/wrapper/maven-wrapper.properties" "Maven Wrapper configuration does not exist"
require_text ".mvn/wrapper/maven-wrapper.properties" "/apache-maven/4.0.0-rc-6/" \
    "Mason PoC requires Maven Wrapper 4.0.0-rc-6"
require_file ".github/workflows/release.yml" "Production release workflow does not exist"
require_text ".github/workflows/release.yml" "Verify Maven 4 Central readiness" \
    "Production Central publication must guard Maven 4 final and Central readiness"
require_text ".github/workflows/release.yml" 'MAVEN_CENTRAL_MAVEN4_READY' \
    "Production Central publication must require an explicit Maven 4 Central readiness flag"
require_text ".github/workflows/release.yml" './mvnw -B -T 1 -Prelease -DskipTests deploy' \
    "Production Central publication must use the Maven 4 wrapper directly"
if grep -Fq -- 'MAVEN_3_VERSION' "${repository_root}/.github/workflows/release.yml" ||
   grep -Fq -- 'generate-maven3-publication-tree.sh' "${repository_root}/.github/workflows/release.yml"; then
    fail "Production release workflow must not contain Maven 3 publication compatibility"
fi

echo "Mason PoC source contract verification passed: ${reachable_yaml_count} reachable YAML reactor POMs."
