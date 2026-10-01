#!/usr/bin/env bash

# Print the reactor project version.
# Maven 4.0.0-rc-7 quiet mode writes its distribution version to stdout before a
# forced expression result. Earlier 4.0 candidates wrapped that result as
# "[INFO] [stdout] <version>".

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
# Tests inject a fake Maven executable. Production runs use the checked-in wrapper.
maven_executable="${MAVEN_PROJECT_VERSION_EXECUTABLE:-${repo_root}/mvnw}"

maven_distribution_version="$(
    sed -n 's#.*apache-maven-\([^/]*\)-bin\.zip#\1#p' \
        "${repo_root}/.mvn/wrapper/maven-wrapper.properties"
)"

if [[ -z "${maven_distribution_version}" ]]; then
    echo "Could not read the Maven distribution version from the wrapper properties." >&2
    exit 1
fi

cd "${repo_root}"
project_version="$(
    "${maven_executable}" -q help:evaluate -Dexpression=project.version -DforceStdout |
        sed -e 's/^\[INFO\] \[stdout\] //' |
        awk -v maven="${maven_distribution_version}" '
            BEGIN { value = "" }
            NF && $0 != maven && $0 !~ /^\[/ { value = $0 }
            END {
                if (value == "") {
                    exit 1
                }
                print value
            }
        '
)"

printf '%s\n' "${project_version}"
