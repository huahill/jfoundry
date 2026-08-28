#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="${script_dir}/verify-mason-model-equivalence.sh"

if [[ ! -x "${verifier}" ]]; then
    echo "Mason model equivalence verifier does not exist or is not executable: ${verifier}" >&2
    exit 1
fi

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-mason-model-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

write_model() {
    local path="$1"
    local dependency_version="$2"
    local plugin_goal="$3"
    local profile_id="$4"
    local scm_inherit="$5"

    printf '%s\n' \
        '<project xmlns="http://maven.apache.org/POM/4.0.0" child.project.url.inherit.append.path="false">' \
        '  <modelVersion>4.0.0</modelVersion>' \
        '  <groupId>example</groupId><artifactId>sample</artifactId><version>1</version>' \
        '  <dependencies><dependency><groupId>example</groupId><artifactId>library</artifactId>' \
        "    <version>${dependency_version}</version></dependency></dependencies>" \
        '  <build><plugins><plugin><groupId>example</groupId><artifactId>plugin</artifactId>' \
        "    <executions><execution><goals><goal>${plugin_goal}</goal></goals></execution></executions>" \
        '  </plugin></plugins></build>' \
        "  <profiles><profile><id>${profile_id}</id></profile></profiles>" \
        "  <scm child.scm.url.inherit.append.path=\"${scm_inherit}\"><url>https://example.invalid</url></scm>" \
        '</project>' > "${path}"
}

expect_difference() {
    local mutation="$1"
    local candidate="$2"
    local output

    if output="$("${verifier}" --compare-files "${fixture_root}/baseline.xml" "${candidate}" 2>&1)"; then
        echo "Expected model difference for mutation: ${mutation}" >&2
        exit 1
    fi
    if [[ "${output}" != *"Maven model difference"* ]]; then
        echo "Unexpected failure for mutation ${mutation}:" >&2
        echo "${output}" >&2
        exit 1
    fi
}

write_model "${fixture_root}/baseline.xml" "1.0" "verify" "release" "false"
write_model "${fixture_root}/equivalent.xml" "1.0" "verify" "release" "false"
"${verifier}" --compare-files "${fixture_root}/baseline.xml" "${fixture_root}/equivalent.xml"

write_model "${fixture_root}/dependency.xml" "2.0" "verify" "release" "false"
expect_difference "dependency version" "${fixture_root}/dependency.xml"

write_model "${fixture_root}/plugin.xml" "1.0" "package" "release" "false"
expect_difference "plugin goal" "${fixture_root}/plugin.xml"

write_model "${fixture_root}/profile.xml" "1.0" "verify" "publishing" "false"
expect_difference "profile id" "${fixture_root}/profile.xml"

write_model "${fixture_root}/scm.xml" "1.0" "verify" "release" "true"
expect_difference "SCM inheritance attribute" "${fixture_root}/scm.xml"

echo "Mason model equivalence verifier self-test passed."
