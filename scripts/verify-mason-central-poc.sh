#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
generator="${repository_root}/scripts/generate-maven3-publication-tree.sh"
capture_server="${repository_root}/scripts/support/CaptureCentralUpload.java"
settings_file="${repository_root}/scripts/support/mason-central-poc-settings.xml"
set_property_xslt="${repository_root}/scripts/support/set-maven-property.xsl"

assert_loopback_base_url() {
    local base_url="$1"

    if [[ ! "${base_url}" =~ ^http://127\.0\.0\.1:[0-9]+$ ]]; then
        echo "Central PoC base URL must be an explicit IPv4 loopback URL: ${base_url}" >&2
        return 1
    fi
}

assert_gpg_home() {
    local gpg_home_path="$1"
    local agent_socket

    agent_socket="$(gpgconf --homedir "${gpg_home_path}" --list-dirs agent-socket)"
    if (( ${#agent_socket} >= 100 )); then
        echo "Central PoC GPG agent socket path must be shorter than 100 characters: ${agent_socket}" >&2
        return 1
    fi
}

read_xml_value() {
    local xml_file="$1"
    local xpath="$2"

    xmllint --xpath "string(${xpath})" "${xml_file}"
}

assert_publication_tree() {
    local publication_tree_path="$1"
    local expected_version="$2"
    local root_pom="${publication_tree_path}/pom.xml"
    local domain_pom="${publication_tree_path}/jfoundry-core/jfoundry-domain/pom.xml"
    local spring_parent_pom="${publication_tree_path}/jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"
    local project_xpath="/*[local-name()='project']"

    if [[ "$(read_xml_value "${root_pom}" "${project_xpath}/*[local-name()='version']")" != "${expected_version}" ]]; then
        echo "Central PoC publication tree has an unexpected root version." >&2
        return 1
    fi
    if [[ "$(read_xml_value "${spring_parent_pom}" "${project_xpath}/*[local-name()='properties']/*[local-name()='jfoundry.version']")" != "${expected_version}" ]]; then
        echo "Central PoC publication tree has a stale jfoundry.version property." >&2
        return 1
    fi
    if [[ "$(read_xml_value "${domain_pom}" "${project_xpath}/*[local-name()='parent']/*[local-name()='relativePath']")" != "../../pom.xml" ]]; then
        echo "Central PoC publication tree has a non-XML domain parent path." >&2
        return 1
    fi
    if rg -l -F '1.4.0-SNAPSHOT' "${publication_tree_path}" --glob 'pom.xml' | grep -q .; then
        echo "Central PoC publication tree still contains 1.4.0-SNAPSHOT." >&2
        return 1
    fi
}

if [[ "${1:-}" == "--check-base-url" ]]; then
    if [[ "$#" -ne 2 ]]; then
        echo "Usage: $0 --check-base-url <url>" >&2
        exit 2
    fi
    assert_loopback_base_url "$2"
    exit 0
fi
if [[ "${1:-}" == "--check-gpg-home" ]]; then
    if [[ "$#" -ne 2 ]]; then
        echo "Usage: $0 --check-gpg-home <path>" >&2
        exit 2
    fi
    assert_gpg_home "$2"
    exit 0
fi
if [[ "${1:-}" == "--check-publication-tree" ]]; then
    if [[ "$#" -ne 3 ]]; then
        echo "Usage: $0 --check-publication-tree <tree> <expected-version>" >&2
        exit 2
    fi
    assert_publication_tree "$2" "$3"
    exit 0
fi

if [[ "$#" -ne 0 ]]; then
    echo "Usage: $0" >&2
    exit 2
fi
if [[ -n "${CENTRAL_USERNAME:-}" || -n "${CENTRAL_PASSWORD:-}" ]]; then
    echo "Central credential environment variables must be unset for the no-upload PoC." >&2
    exit 1
fi

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-central-poc.XXXXXX")"
publication_tree="${temporary_root}/publication-tree"
local_repository="${temporary_root}/local-repository"
gpg_home="$(mktemp -d /tmp/jf-mason-gpg.XXXXXX)"
deferred_directory="${temporary_root}/central-deferred"
staging_directory="${temporary_root}/central-staging"
output_directory="${temporary_root}/central-output"
port_file="${temporary_root}/capture-port"
request_file="${temporary_root}/captured-request.txt"
server_log="${temporary_root}/capture-server.log"
maven_log="${temporary_root}/maven-central-poc.log"
server_pid=""

cleanup() {
    if [[ -n "${server_pid}" ]] && kill -0 "${server_pid}" 2>/dev/null; then
        kill "${server_pid}" 2>/dev/null || true
        wait "${server_pid}" 2>/dev/null || true
    fi
    gpgconf --homedir "${gpg_home}" --kill gpg-agent >/dev/null 2>&1 || true
    rm -rf "${temporary_root}" "${gpg_home}"
}
trap cleanup EXIT

"${generator}" "${publication_tree}"
mkdir -p "${local_repository}" "${gpg_home}" \
    "${deferred_directory}" "${staging_directory}" "${output_directory}"
chmod 700 "${gpg_home}"
assert_gpg_home "${gpg_home}"

(
    cd "${publication_tree}"
    ./mvnw -B -N \
        org.codehaus.mojo:versions-maven-plugin:2.19.1:set \
        -DnewVersion=1.4.0-POC \
        -DprocessAllModules=true \
        -DgenerateBackupPoms=false
)
spring_parent_pom="${publication_tree}/jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"
updated_spring_parent_pom="${spring_parent_pom}.updated"
xsltproc \
    --stringparam propertyName jfoundry.version \
    --stringparam newValue 1.4.0-POC \
    "${set_property_xslt}" "${spring_parent_pom}" \
    > "${updated_spring_parent_pom}"
mv "${updated_spring_parent_pom}" "${spring_parent_pom}"
assert_publication_tree "${publication_tree}" "1.4.0-POC"

gpg --batch --homedir "${gpg_home}" --passphrase "" \
    --quick-generate-key "JFoundry Mason PoC <mason-poc@invalid.example>" rsa2048 sign 0
gpg_fingerprint="$(
    gpg --batch --homedir "${gpg_home}" --with-colons --list-secret-keys |
        awk -F: '$1 == "fpr" { print $10; exit }'
)"
if [[ -z "${gpg_fingerprint}" ]]; then
    echo "Failed to generate the temporary Mason Central PoC signing key." >&2
    exit 1
fi

java "${capture_server}" "${port_file}" "${request_file}" > "${server_log}" 2>&1 &
server_pid="$!"
for _ in $(seq 1 100); do
    [[ -s "${port_file}" ]] && break
    if ! kill -0 "${server_pid}" 2>/dev/null; then
        cat "${server_log}" >&2
        echo "Central PoC capture server stopped before publishing." >&2
        exit 1
    fi
    sleep 0.1
done
if [[ ! -s "${port_file}" ]]; then
    echo "Central PoC capture server did not publish its loopback port." >&2
    exit 1
fi

capture_port="$(<"${port_file}")"
central_base_url="http://127.0.0.1:${capture_port}"
assert_loopback_base_url "${central_base_url}"

if ! (
    cd "${publication_tree}"
    ./mvnw -B -T 1 -Pmason-central-poc -DskipTests \
        -Dmaven.deploy.skip=true \
        "-Dmaven.repo.local=${local_repository}" \
        "-s${settings_file}" \
        "-Dmason.central.poc.baseUrl=${central_base_url}" \
        "-Dmason.central.poc.gpg.homedir=${gpg_home}" \
        "-Dmason.central.poc.gpg.keyname=${gpg_fingerprint}" \
        "-Dgpg.passphrase=" \
        "-Dmason.central.poc.deferredDirectory=${deferred_directory}" \
        "-Dmason.central.poc.stagingDirectory=${staging_directory}" \
        "-Dmason.central.poc.outputDirectory=${output_directory}" \
        deploy
) > "${maven_log}" 2>&1; then
    tail -200 "${maven_log}" >&2
    exit 1
fi

wait "${server_pid}"
server_pid=""

grep -Fq 'method=POST' "${request_file}"
grep -Fq 'path=/api/v1/publisher/upload' "${request_file}"
grep -Fq 'authorizationPresent=true' "${request_file}"

bundle="${output_directory}/jfoundry-mason-central-poc.zip"
if [[ ! -f "${bundle}" ]]; then
    echo "Central Publishing Plugin did not create the local bundle." >&2
    exit 1
fi
bundle_entries="$(unzip -Z1 "${bundle}")"
required_entries=(
    "io/github/xfoundries/jfoundry-parent/1.4.0-POC/jfoundry-parent-1.4.0-POC.pom"
    "io/github/xfoundries/jfoundry-domain/1.4.0-POC/jfoundry-domain-1.4.0-POC.jar"
    "io/github/xfoundries/jfoundry-domain/1.4.0-POC/jfoundry-domain-1.4.0-POC-sources.jar"
    "io/github/xfoundries/jfoundry-domain/1.4.0-POC/jfoundry-domain-1.4.0-POC-javadoc.jar"
    "io/github/xfoundries/jfoundry-spring-boot-starter/1.4.0-POC/jfoundry-spring-boot-starter-1.4.0-POC.pom"
    "io/github/xfoundries/jfoundry-helidon-dependencies/1.4.0-POC/jfoundry-helidon-dependencies-1.4.0-POC.pom"
)
for entry in "${required_entries[@]}"; do
    if [[ "${bundle_entries}" != *"${entry}"* ]]; then
        echo "Central bundle is missing: ${entry}" >&2
        exit 1
    fi
done
if [[ "${bundle_entries}" != *".asc"* || "${bundle_entries}" != *".sha256"* ]]; then
    echo "Central bundle does not contain signatures and SHA-256 checksums." >&2
    exit 1
fi

tail -40 "${maven_log}"
cat "${request_file}"
echo "Maven 4 Central no-upload PoC verification passed."
