#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runner="${repository_root}/scripts/verify-mason-central-poc.sh"
set_property_xslt="${repository_root}/scripts/support/set-maven-property.xsl"

if [[ ! -x "${runner}" ]]; then
    echo "Maven 4 Central PoC verifier does not exist or is not executable: ${runner}" >&2
    exit 1
fi

if "${runner}" --check-base-url "https://central.sonatype.com" >/dev/null 2>&1; then
    echo "Central PoC accepted a non-loopback base URL." >&2
    exit 1
fi
"${runner}" --check-base-url "http://127.0.0.1:8080"

long_gpg_home="/tmp/$(printf 'gpg-path-%.0s' {1..12})"
if "${runner}" --check-gpg-home "${long_gpg_home}" >/dev/null 2>&1; then
    echo "Central PoC accepted an unsafe GPG agent socket path." >&2
    exit 1
fi
"${runner}" --check-gpg-home "/tmp/jf-mason-gpg.123456"

temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-central-guard-test.XXXXXX")"
trap 'rm -rf "${temporary_root}"' EXIT
publication_tree="${temporary_root}/publication-tree"
mkdir -p \
    "${publication_tree}/jfoundry-core/jfoundry-domain" \
    "${publication_tree}/jfoundry-boms/jfoundry-spring-boot-parent"
printf '%s\n' \
    '<project><version>1.4.0-POC</version></project>' \
    > "${publication_tree}/pom.xml"
printf '%s\n' \
    '<project><parent><relativePath>../../pom.xml</relativePath></parent></project>' \
    > "${publication_tree}/jfoundry-core/jfoundry-domain/pom.xml"
printf '%s\n' \
    '<project><properties><jfoundry.version>1.4.0-SNAPSHOT</jfoundry.version></properties></project>' \
    > "${publication_tree}/jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"
spring_parent_pom="${publication_tree}/jfoundry-boms/jfoundry-spring-boot-parent/pom.xml"
xsltproc \
    --stringparam propertyName jfoundry.version \
    --stringparam newValue 1.4.0-POC \
    "${set_property_xslt}" "${spring_parent_pom}" \
    > "${spring_parent_pom}.updated"
mv "${spring_parent_pom}.updated" "${spring_parent_pom}"
"${runner}" --check-publication-tree "${publication_tree}" "1.4.0-POC"
sed -i.bak 's/1.4.0-POC/1.4.0-SNAPSHOT/' \
    "${spring_parent_pom}"
rm "${spring_parent_pom}.bak"
if "${runner}" --check-publication-tree "${publication_tree}" "1.4.0-POC" >/dev/null 2>&1; then
    echo "Central PoC accepted a publication tree with a stale JFoundry version." >&2
    exit 1
fi

profile_files=(
    "pom.yaml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml"
)
for profile_file in "${profile_files[@]}"; do
    profile_path="${repository_root}/${profile_file}"
    grep -Fq 'id: mason-central-poc' "${profile_path}" || {
        echo "Central PoC profile does not exist: ${profile_file}" >&2
        exit 1
    }
    grep -Fq 'maven.deploy.skip: true' "${profile_path}" || {
        echo "Central PoC profile does not skip standard deploy: ${profile_file}" >&2
        exit 1
    }
    grep -Fq 'id: mason-central-poc-publish' "${profile_path}" || {
        echo "Central PoC publish execution does not exist: ${profile_file}" >&2
        exit 1
    }
    grep -Fq 'phase: deploy' "${profile_path}" || {
        echo "Central PoC publish execution is not bound to deploy: ${profile_file}" >&2
        exit 1
    }
    grep -Fq 'goals: [publish]' "${profile_path}" || {
        echo "Central PoC profile does not invoke publish explicitly: ${profile_file}" >&2
        exit 1
    }
done

echo "Maven 4 Central PoC guard self-test passed."
