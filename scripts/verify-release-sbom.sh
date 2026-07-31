#!/usr/bin/env bash

set -euo pipefail

pom_file="${1:-pom.xml}"

if [[ ! -f "${pom_file}" ]]; then
    echo "Maven POM does not exist: ${pom_file}" >&2
    exit 1
fi

require_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${pom_file}"; then
        echo "Release SBOM configuration must contain: ${text}" >&2
        exit 1
    fi
}

require_text "<id>release</id>"
require_text "<groupId>org.cyclonedx</groupId>"
require_text "<artifactId>cyclonedx-maven-plugin</artifactId>"
require_text "<phase>package</phase>"
require_text "<goal>makeAggregateBom</goal>"
require_text "<outputFormat>all</outputFormat>"
require_text "<includeTestScope>false</includeTestScope>"

echo "Release SBOM verification passed: ${pom_file}"
