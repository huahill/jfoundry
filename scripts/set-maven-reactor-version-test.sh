#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
updater="${script_dir}/set-maven-reactor-version.py"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/jfoundry-maven-version-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

mkdir -p "${fixture_root}/module" "${fixture_root}/standalone" "${fixture_root}/spring-parent" "${fixture_root}/module/target"
cat > "${fixture_root}/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0">
    <modelVersion>4.1.0</modelVersion>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>fixture-parent</artifactId>
    <version>1.0.0</version>
    <properties><project.build.outputTimestamp>2026-08-23T16:38:25Z</project.build.outputTimestamp></properties>
    <subprojects><subproject>module</subproject><subproject>standalone</subproject><subproject>spring-parent</subproject></subprojects>
</project>
XML
cat > "${fixture_root}/module/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0">
    <modelVersion>4.1.0</modelVersion>
    <parent><groupId>io.github.xfoundries</groupId><artifactId>fixture-parent</artifactId><version>1.0.0</version><relativePath>../pom.xml</relativePath></parent>
    <artifactId>fixture-module</artifactId>
    <!-- This comment must survive the version update. -->
</project>
XML
cat > "${fixture_root}/standalone/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0">
    <modelVersion>4.1.0</modelVersion><groupId>io.github.xfoundries</groupId><artifactId>fixture-bom</artifactId><version>1.0.0</version><packaging>pom</packaging>
</project>
XML
cat > "${fixture_root}/spring-parent/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0">
    <modelVersion>4.1.0</modelVersion><groupId>io.github.xfoundries</groupId><artifactId>fixture-spring-parent</artifactId><version>1.0.0</version>
    <properties><jfoundry.version>1.0.0</jfoundry.version><project.build.outputTimestamp>2026-08-23T16:38:24Z</project.build.outputTimestamp></properties>
</project>
XML
cat > "${fixture_root}/module/target/pom.xml" <<'XML'
<project><version>1.0.0</version></project>
XML

python3 "${updater}" "${fixture_root}" "2.0.0-SNAPSHOT"

grep -Fq '<version>2.0.0-SNAPSHOT</version>' "${fixture_root}/pom.xml"
grep -Fq '<version>2.0.0-SNAPSHOT</version>' "${fixture_root}/module/pom.xml"
grep -Fq '<jfoundry.version>2.0.0-SNAPSHOT</jfoundry.version>' "${fixture_root}/spring-parent/pom.xml"
grep -Fq '2026-08-23T16:38:25Z' "${fixture_root}/pom.xml"
grep -Fqx '    <!-- This comment must survive the version update. -->' "${fixture_root}/module/pom.xml"
grep -Fq '<version>1.0.0</version>' "${fixture_root}/module/target/pom.xml"

cat >> "${fixture_root}/module/pom.xml" <<'XML'
<dependencies><dependency><groupId>org.example</groupId><artifactId>unrelated</artifactId><version>2.0.0-SNAPSHOT</version></dependency></dependencies>
XML
if python3 "${updater}" "${fixture_root}" "3.0.0-SNAPSHOT" >/dev/null 2>&1; then
    echo "Expected an unclassified reactor-version occurrence to be rejected." >&2
    exit 1
fi

echo "Maven reactor version updater tests passed."
