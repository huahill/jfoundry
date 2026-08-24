#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-supply-chain-workflows.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected supply-chain workflow verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects() {
    if bash "${VERIFY_SCRIPT}" "$1" >/dev/null 2>&1; then
        echo "Expected supply-chain workflow verification to reject $1." >&2
        exit 1
    fi
}

assert_rejects_with_message() {
    local output
    if output="$(bash "${VERIFY_SCRIPT}" "$1" 2>&1)"; then
        echo "Expected supply-chain workflow verification to reject $1." >&2
        exit 1
    fi
    if ! grep -Fqx -- "$2" <<< "${output}"; then
        echo "Expected supply-chain workflow verification to reject $1 with: $2" >&2
        echo "Actual output: ${output}" >&2
        exit 1
    fi
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
mkdir -p "${temp_dir}/.github/workflows"

write_compliant_dependabot() {
    cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-spring-boot-platform:
        patterns:
          - org.springframework.boot:spring-boot-dependencies
          - org.springframework.boot:spring-boot-starter-parent
          - org.springframework.boot:spring-boot-maven-plugin
        update-types: [patch, minor]
      jfoundry-quarkus-platform:
        patterns:
          - io.quarkus.platform:quarkus-bom
          - io.quarkus:quarkus-extension-maven-plugin
          - io.quarkus:quarkus-extension-processor
          - io.quarkus:quarkus-maven-plugin
        update-types: [patch, minor]
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
}

write_compliant_auto_merge_workflow() {
    cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: write
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        if: steps.scope.outputs.is_maven_update == 'true'
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3
        with:
          github-token: ${{ github.token }}

      - name: Check automatic merge eligibility
        id: eligibility
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          UPDATE_TYPE: ${{ steps.dependabot-metadata.outputs.update-type }}
        run: |
          set -euo pipefail
          if [[ "${UPDATE_TYPE}" == 'version-update:semver-patch' ]]; then
            echo "is_patch_update=true" >> "${GITHUB_OUTPUT}"
          else
            echo "is_patch_update=false" >> "${GITHUB_OUTPUT}"
          fi

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.eligibility.outputs.is_patch_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
}

replace_in_auto_merge_workflow() {
    local expected="$1"
    local replacement="$2"

    ruby - "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" "${expected}" "${replacement}" <<'RUBY'
path, expected, replacement = ARGV
content = File.read(path)
abort "Expected workflow text was not found: #{expected}" unless content.sub!(expected, replacement)
File.write(path, content)
RUBY
}

write_compliant_dependabot
ruby - "${temp_dir}/.github/dependabot.yml" <<'RUBY'
path = ARGV.fetch(0)
content = File.read(path)
content.sub!("        update-types: [patch]\n", <<~YAML)
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
YAML
File.write(path, content)
RUBY
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    steps:
      - uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin,actions
      - uses: github/codeql-action/analyze@v4
YAML
cat > "${temp_dir}/.github/workflows/release.yml" <<'YAML'
jobs:
  publish:
    steps:
      - uses: actions/upload-artifact@v4
        with:
          path: release-evidence
YAML

assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/ci.yml" <<'YAML'
jobs:
  dependency-review:
    name: Dependency Review
    if: github.event_name == 'pull_request'
    permissions:
      contents: read
      pull-requests: read
    steps:
      - uses: actions/dependency-review-action@v4
        with:
          fail-on-severity: high
  merge-gate:
    needs:
      - dependency-review
    steps:
      - run: echo '${{ needs.dependency-review.result }}'
  consumer-pom-verification:
    steps:
      - name: Test dependency boundary verifier
        run: bash scripts/verify-dependency-boundaries-test.sh
      - name: Verify dependency boundaries
        run: bash scripts/verify-dependency-boundaries.sh
      - name: Test Consumer POM verification
        run: bash scripts/verify-consumer-pom-test.sh
      - name: Verify release POM metadata
        run: bash scripts/verify-release-pom-metadata.sh
      - name: Test release POM metadata verification
        run: bash scripts/verify-release-pom-metadata-test.sh
      - name: Verify reactor Consumer POMs
        run: |
          ./mvnw -Dmaven.repo.local="${consumer_pom_repository}" install
          maven_3="$(command -v mvn)"
          if [[ "$("${maven_3}" --version)" != "Apache Maven 3."* ]]; then exit 1; fi
          bash scripts/verify-consumer-pom.sh "${consumer_pom_repository}" "${version}" \
            "${maven_3}" "$(pwd)/mvnw"
YAML
assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/snapshot.yml" <<'YAML'
on:
  push:
    branches: [main]
jobs:
  publish-snapshot:
    steps:
      - run: |
          version="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)"
          test "${version}" = "1.0.0-SNAPSHOT"
YAML
assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/snapshot.yml" <<'YAML'
on:
  push:
    branches: [main]
jobs:
  publish-snapshot:
    steps:
      - run: |
          version="$(
            ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout |
              sed -n 's/^\[INFO\] \[stdout\] //p' |
              tail -n 1
          )"
          case "${version}" in
            *-SNAPSHOT)
              echo "is_snapshot=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_snapshot=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac
      - name: Publish SNAPSHOT
        if: steps.version.outputs.is_snapshot == 'true'
        run: ./mvnw deploy
YAML
assert_rejects "${temp_dir}"
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    strategy:
      matrix:
        include:
          - language: java-kotlin
            build-mode: manual
          - language: actions
            build-mode: none
    steps:
      - uses: github/codeql-action/init@v4
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
      - uses: github/codeql-action/analyze@v4
YAML
assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    strategy:
      matrix:
        include:
          - language: java-kotlin
            build-mode: manual
          - language: actions
            build-mode: none
    steps:
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
      - uses: github/codeql-action/init@v4
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
      - uses: github/codeql-action/analyze@v4
YAML
assert_rejects "${temp_dir}"
cat > "${temp_dir}/.github/workflows/codeql.yml" <<'YAML'
permissions:
  contents: read
  security-events: write
jobs:
  analyze:
    strategy:
      matrix:
        include:
          - language: java-kotlin
            build-mode: manual
          - language: actions
            build-mode: none
    steps:
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
      - uses: github/codeql-action/init@v4
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
      - name: Build Java sources
        if: matrix.language == 'java-kotlin'
        run: ./mvnw -B -DskipTests -pl '!jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests' package
      - uses: github/codeql-action/analyze@v4
YAML
cat > "${temp_dir}/.github/workflows/prepare-snapshot.yml" <<'YAML'
on:
  workflow_run:
    workflows:
      - Release
    types: [completed]
permissions:
  contents: write
  pull-requests: write
jobs:
  prepare:
    steps:
      - run: git tag --points-at sha
      - run: ./mvnw versions:set
      - run: git push --set-upstream origin branch
      - run: gh pr create
YAML
assert_accepts "${temp_dir}"

write_compliant_dependabot
ruby - "${temp_dir}/.github/dependabot.yml" <<'RUBY'
require "yaml"

path = ARGV.fetch(0)
config = YAML.safe_load(File.read(path), aliases: false)
groups = config.fetch("updates").first.fetch("groups")
config.fetch("updates").first["groups"] = {
  "jfoundry-maven-patches" => groups.fetch("jfoundry-maven-patches"),
  "jfoundry-spring-boot-platform" => groups.fetch("jfoundry-spring-boot-platform"),
  "jfoundry-quarkus-platform" => groups.fetch("jfoundry-quarkus-platform")
}
File.write(path, YAML.dump(config))
RUBY
assert_rejects_with_message "${temp_dir}" "Dependabot update policy is invalid: Maven groups must be ordered as jfoundry-spring-boot-platform, jfoundry-quarkus-platform, jfoundry-maven-patches"

write_compliant_dependabot
ruby - "${temp_dir}/.github/dependabot.yml" <<'RUBY'
require "yaml"

path = ARGV.fetch(0)
config = YAML.safe_load(File.read(path), aliases: false)
patterns = config.fetch("updates").first.fetch("groups").fetch("jfoundry-quarkus-platform").fetch("patterns")
patterns.delete("io.quarkus.platform:quarkus-bom")
File.write(path, YAML.dump(config))
RUBY
assert_rejects_with_message "${temp_dir}" "Dependabot update policy is invalid: jfoundry-quarkus-platform must group the complete supported coordinate set for patch and minor updates"

write_compliant_dependabot
ruby - "${temp_dir}/.github/workflows/prepare-snapshot.yml" <<'RUBY'
path = ARGV.fetch(0)
content = File.read(path)
needle = "      - run: gh pr create\n"
abort "Expected prepare-snapshot marker" unless content.sub!(needle, "#{needle}      - run: sed -i jfoundry-boms/jfoundry-spring-cloud-parent/pom.xml\n")
File.write(path, content)
RUBY
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: "*"
        update-types:
          - version-update:semver-minor
          - version-update:semver-major
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch, minor]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      renamed-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: [org.jfoundry:*]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [minor]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
      jfoundry-maven-minors:
        patterns: ["*"]
        update-types: [minor]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: org.example:manual-policy
      - dependency-name: io.quarkus.platform:quarkus-bom
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      github-codeql-action:
        patterns: [github/codeql-action/*]
YAML
assert_rejects "${temp_dir}"

cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
updates:
  - package-ecosystem: maven
  - package-ecosystem: github-actions
    malformed: [
YAML
assert_rejects_with_message "${temp_dir}" "Dependabot update policy is invalid: could not safely parse YAML"

cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
# package-ecosystem: maven
# package-ecosystem: github-actions
updates: !ruby/object:Policy
  name: dependabot
YAML
assert_rejects_with_message "${temp_dir}" "Dependabot update policy is invalid: could not safely parse YAML"

cat > "${temp_dir}/.github/dependabot.yml" <<'YAML'
version: 2
# package-ecosystem: maven
# package-ecosystem: github-actions
updates:
  - malformed-update-entry
YAML
assert_rejects_with_message "${temp_dir}" "Dependabot update policy is invalid: each updates entry must be a mapping"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef97 # v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail
          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail
          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail
          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        run: gh pr merge "${PR_NUMBER}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
          UPDATE_TYPE: ${{ steps.dependabot-metadata.outputs.update-type }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          if [[ "${UPDATE_TYPE}" != 'version-update:semver-patch' ]]; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.dependency_policy.outputs.is_dependency_policy == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
cat > "${temp_dir}/.github/workflows/auto-merge-dependabot.yml" <<'YAML'
name: Auto-merge Dependabot Maven updates

on:
  pull_request_target:
    types: [opened, reopened, synchronize]

permissions:
  contents: read
  pull-requests: write

jobs:
  enable-auto-merge:
    if: >-
      github.event.pull_request.user.login == 'dependabot[bot]' &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: dependabot-metadata
        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98 # v3
        with:
          github-token: "${{ secrets.GITHUB_TOKEN }}"

      - name: Verify Maven-only update
        id: scope
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          files="$(gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files" --paginate --jq '.[].filename')"
          if [[ -z "${files}" ]] || grep -Evq '(^|/)pom\.xml$' <<< "${files}"; then
            echo "is_maven_update=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi
          echo "is_maven_update=true" >> "${GITHUB_OUTPUT}"

      - name: Reject dependency policy gate
        id: dependency_policy
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"* | *"org.example:manual-policy"*)
              echo "is_dependency_policy=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_dependency_policy=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
        # The legacy verifier below still checks this prior command form as a literal string.
        # gh pr merge "${PR_NUMBER}" --auto --rebase
        run: gh pr merge "${PR_NUMBER}" --repo "${REPOSITORY}" --auto --rebase
YAML
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow "contents: write" "contents: read"
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow "github.event.pull_request.base.ref == 'main'" "github.event.pull_request.base.ref == 'develop'"
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow "dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98" "dependabot/fetch-metadata@v3"
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow "version-update:semver-patch" "version-update:semver-minor"
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow $'steps.scope.outputs.is_maven_update == \'true\' &&\n          steps.eligibility.outputs.is_patch_update == \'true\'' "steps.scope.outputs.is_maven_update == 'true'"
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow $'steps:\n' $'steps:\n      - name: Fetch Dependabot metadata\n        id: dependabot-metadata\n        uses: dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98\n'
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow $'steps:\n' $'steps:\n      - run: gh pr checkout "${PR_NUMBER}"\n'
assert_rejects "${temp_dir}"

write_compliant_dependabot
write_compliant_auto_merge_workflow
replace_in_auto_merge_workflow $'GH_TOKEN: ${{ github.token }}' $'GH_TOKEN: ${{ secrets[\'GITHUB_TOKEN\'] }}'
assert_rejects "${temp_dir}"

cat > "${temp_dir}/.github/workflows/snapshot.yml" <<'YAML'
on:
  push:
    branches: [main]
jobs:
  publish-snapshot:
    steps:
      - run: |
          version="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)"
          case "${version}" in
            *-SNAPSHOT) ;;
            *) exit 0 ;;
          esac
YAML
assert_rejects "${temp_dir}"

cp "${temp_dir}/.github/workflows/ci.yml" "${temp_dir}/.github/workflows/ci.yml.bak"
ruby - "${temp_dir}/.github/workflows/ci.yml" <<'RUBY'
path = ARGV.fetch(0)
File.write(path, File.read(path).lines.reject { |line| line.include?("bash scripts/verify-dependency-boundaries.sh") }.join)
RUBY
assert_rejects_with_message "${temp_dir}" ".github/workflows/ci.yml must contain: bash scripts/verify-dependency-boundaries.sh"
mv "${temp_dir}/.github/workflows/ci.yml.bak" "${temp_dir}/.github/workflows/ci.yml"

cp "${temp_dir}/.github/workflows/ci.yml" "${temp_dir}/.github/workflows/ci.yml.bak"
ruby - "${temp_dir}/.github/workflows/ci.yml" <<'RUBY'
path = ARGV.fetch(0)
File.write(path, File.read(path).lines.reject { |line| line.include?("bash scripts/verify-consumer-pom.sh") }.join)
RUBY
assert_rejects_with_message "${temp_dir}" '.github/workflows/ci.yml must contain: bash scripts/verify-consumer-pom.sh "${consumer_pom_repository}" "${version}"'
mv "${temp_dir}/.github/workflows/ci.yml.bak" "${temp_dir}/.github/workflows/ci.yml"

cp "${temp_dir}/.github/workflows/ci.yml" "${temp_dir}/.github/workflows/ci.yml.bak"
ruby - "${temp_dir}/.github/workflows/ci.yml" <<'RUBY'
path = ARGV.fetch(0)
File.write(path, File.read(path).lines.reject { |line| line.include?("bash scripts/verify-dependency-boundaries-test.sh") }.join)
RUBY
assert_rejects_with_message "${temp_dir}" ".github/workflows/ci.yml must contain: bash scripts/verify-dependency-boundaries-test.sh"
mv "${temp_dir}/.github/workflows/ci.yml.bak" "${temp_dir}/.github/workflows/ci.yml"

rm "${temp_dir}/.github/dependabot.yml"
assert_rejects "${temp_dir}"
assert_accepts "${ROOT_DIR}"

echo "Supply-chain workflow verification tests passed."
