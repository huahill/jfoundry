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
      jfoundry-maven-patches:
        patterns: ["*"]
        update-types: [patch]
    ignore:
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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

      - name: Check automatic merge eligibility
        id: eligibility
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail
          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_eligible=false" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_eligible=true" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.eligibility.outputs.is_eligible == 'true'
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
      - name: Test Consumer POM verification
        run: bash scripts/verify-consumer-pom-test.sh
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
assert_accepts "${temp_dir}"

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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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
      - dependency-name: org.springframework.boot:spring-boot-dependencies
      - dependency-name: org.springframework.boot:spring-boot-starter-parent
      - dependency-name: org.springframework.boot:spring-boot-maven-plugin
      - dependency-name: org.springframework.cloud:spring-cloud-dependencies
      - dependency-name: com.alibaba.cloud:spring-cloud-alibaba-dependencies
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail
          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
              ;;
          esac

      - name: Enable rebase auto-merge
        if: >-
          steps.scope.outputs.is_maven_update == 'true' &&
          steps.protected-coordinates.outputs.is_protected_coordinate == 'false'
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

      - name: Reject protected Spring coordinates
        id: protected-coordinates
        if: steps.scope.outputs.is_maven_update == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          REPOSITORY: ${{ github.repository }}
          DEPENDENCY_NAMES: ${{ steps.dependabot-metadata.outputs.dependency-names }}
        run: |
          set -euo pipefail

          case ",${DEPENDENCY_NAMES}," in
            *"org.springframework.boot:spring-boot-dependencies"* | *"org.springframework.boot:spring-boot-starter-parent"* | *"org.springframework.boot:spring-boot-maven-plugin"* | *"org.springframework.cloud:spring-cloud-dependencies"* | *"com.alibaba.cloud:spring-cloud-alibaba-dependencies"*)
              echo "is_protected_coordinate=true" >> "${GITHUB_OUTPUT}"
              ;;
            *)
              echo "is_protected_coordinate=false" >> "${GITHUB_OUTPUT}"
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
replace_in_auto_merge_workflow $'id: dependabot-metadata\n        if: steps.scope.outputs.is_maven_update == \'true\'' $'id: dependabot-metadata\n        if: always()'
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
rm "${temp_dir}/.github/dependabot.yml"
assert_rejects "${temp_dir}"
assert_accepts "${ROOT_DIR}"

echo "Supply-chain workflow verification tests passed."
