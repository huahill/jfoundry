#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
updater="${script_dir}/set-mason-reactor-version.rb"
fixture_root="$(mktemp -d)"
trap 'rm -rf "${fixture_root}"' EXIT

mkdir -p "${fixture_root}/module/target" "${fixture_root}/standalone" "${fixture_root}/spring-parent"
cat > "${fixture_root}/pom.yaml" <<'YAML'
modelVersion: 4.0.0
groupId: io.github.xfoundries
artifactId: fixture-parent
version: 1.0.0
properties:
  project.build.outputTimestamp: "2026-08-23T16:38:25Z"
modules:
  - module
  - standalone
  - spring-parent
YAML
cat > "${fixture_root}/module/pom.yaml" <<'YAML'
modelVersion: 4.0.0
parent:
  groupId: io.github.xfoundries
  artifactId: fixture-parent
  version: 1.0.0
  relativePath: ../pom.yaml
artifactId: fixture-module
# This comment must survive the version update.
YAML
cat > "${fixture_root}/standalone/pom.yaml" <<'YAML'
modelVersion: 4.0.0
groupId: io.github.xfoundries
artifactId: fixture-bom
version: 1.0.0
packaging: pom
YAML
cat > "${fixture_root}/spring-parent/pom.yaml" <<'YAML'
modelVersion: 4.0.0
groupId: io.github.xfoundries
artifactId: fixture-spring-parent
version: 1.0.0
properties:
  jfoundry.version: 1.0.0
  project.build.outputTimestamp: "2026-08-23T16:38:24Z"
YAML
cat > "${fixture_root}/module/target/pom.yaml" <<'YAML'
modelVersion: 4.0.0
groupId: io.github.xfoundries
artifactId: generated-model
version: 1.0.0
YAML

ruby --disable-gems "${updater}" "${fixture_root}" "2.0.0-SNAPSHOT"

ruby --disable-gems - "${fixture_root}" <<'RUBY'
require "yaml"

root = ARGV.fetch(0)
load_pom = ->(relative) { YAML.safe_load(File.read(File.join(root, relative)), aliases: true) }
raise "root version was not updated" unless load_pom.call("pom.yaml").fetch("version") == "2.0.0-SNAPSHOT"
raise "child parent version was not updated" unless load_pom.call("module/pom.yaml").dig("parent", "version") == "2.0.0-SNAPSHOT"
raise "standalone version was not updated" unless load_pom.call("standalone/pom.yaml").fetch("version") == "2.0.0-SNAPSHOT"
spring_parent = load_pom.call("spring-parent/pom.yaml")
raise "Spring parent version was not updated" unless spring_parent.fetch("version") == "2.0.0-SNAPSHOT"
raise "jfoundry.version was not updated" unless spring_parent.dig("properties", "jfoundry.version") == "2.0.0-SNAPSHOT"
raise "root output timestamp changed" unless load_pom.call("pom.yaml").dig("properties", "project.build.outputTimestamp") == "2026-08-23T16:38:25Z"
raise "Spring parent output timestamp changed" unless spring_parent.dig("properties", "project.build.outputTimestamp") == "2026-08-23T16:38:24Z"
RUBY

grep -Fqx '# This comment must survive the version update.' "${fixture_root}/module/pom.yaml"
grep -Fqx 'version: 1.0.0' "${fixture_root}/module/target/pom.yaml"

cat >> "${fixture_root}/module/pom.yaml" <<'YAML'
dependencies:
  - groupId: org.example
    artifactId: unrelated
    version: 2.0.0-SNAPSHOT
YAML
if ruby --disable-gems "${updater}" "${fixture_root}" "3.0.0-SNAPSHOT" >/dev/null 2>&1; then
    echo "Expected an unclassified reactor-version occurrence to be rejected." >&2
    exit 1
fi

echo "Mason reactor version updater tests passed."
