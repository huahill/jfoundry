#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-release-workflow.sh"

assert_accepts() {
    if ! bash "${VERIFY_SCRIPT}" "$1"; then
        echo "Expected release workflow verification to succeed for $1." >&2
        exit 1
    fi
}

assert_rejects() {
    if bash "${VERIFY_SCRIPT}" "$1" >/dev/null 2>&1; then
        echo "Expected release workflow verification to reject $1." >&2
        exit 1
    fi
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

safe_workflow="${temp_dir}/safe-release.yml"
cat > "${safe_workflow}" <<'YAML'
name: Release
on:
  workflow_dispatch:
    inputs:
      release_tag:
        required: true
permissions:
  actions: read
  contents: write
  security-events: read
  vulnerability-alerts: read
  attestations: write
  id-token: write
jobs:
  publish:
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ inputs.release_tag }}
      - name: Verify immutable release source
        run: |
          version="$(
            ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout |
              sed -n 's/^\[INFO\] \[stdout\] //p' |
              tail -n 1
          )"
          test "${{ inputs.release_tag }}" = "v${version}"
          test -z "$(git status --porcelain)"
          git fetch origin +refs/heads/main:refs/remotes/origin/main --no-tags
          git merge-base --is-ancestor "$(git rev-parse HEAD)" origin/main
      - name: Verify complete CI
        run: gh run view 1 --json jobs
      - run: ./mvnw -B -Prelease -DskipTests verify
      - name: Verify Maven Central Consumer POMs
        run: |
          version="$(
            ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout |
              sed -n 's/^\[INFO\] \[stdout\] //p' |
              tail -n 1
          )"
          bash scripts/verify-consumer-pom.sh /tmp/repository "${version}" mvn ./mvnw
      - run: ./mvnw -B -Prelease -DskipTests deploy
      - name: Verify Dependabot security alerts
        run: gh api repos/${GITHUB_REPOSITORY}/dependabot/alerts
      - uses: actions/attest-build-provenance@v2
YAML

unsafe_workflow="${temp_dir}/unsafe-release.yml"
cat > "${unsafe_workflow}" <<'YAML'
name: Release
on:
  release:
    types: [published]
jobs:
  publish:
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw versions:set -DnewVersion=1.0.0
      - run: ./mvnw -Prelease deploy
      - run: git push origin main
YAML

assert_rejects "${unsafe_workflow}"
assert_rejects "${safe_workflow}"

complete_workflow="${temp_dir}/complete-release.yml"
cp "${safe_workflow}" "${complete_workflow}"
cat >> "${complete_workflow}" <<'YAML'
      - name: Stage Maven Central deployment
        run: ./mvnw -B -Prelease -DskipTests deploy | tee central-deploy.log
      - name: Check Maven Central publication
        run: |
          central_status="$(
            curl --silent --show-error --output /dev/null --write-out '%{http_code}' --head \
              'https://repo.maven.apache.org/maven2/io/github/xfoundries/jfoundry-parent/1.0.0/jfoundry-parent-${RELEASE_VERSION}.pom'
          )"
          case "${central_status}" in
            200) echo "already_published=true" ;;
            404) echo "already_published=false" ;;
            *)
              echo "Central publication lookup returned unexpected HTTP status: ${central_status}" >&2
              exit 1
              ;;
          esac
      - name: Verify Maven Central publication
        run: test "${PUBLICATION_VISIBLE}" = "true"
      - name: Capture Maven Central deployment details
        if: ${{ always() }}
        run: |
          mkdir -p release-evidence
          printf 'deployment_id=%s\n' "${DEPLOYMENT_ID}" > release-evidence/central-deployment.txt
      - name: Upload Maven Central deployment details
        if: ${{ always() }}
        run: echo release-evidence/central-deployment.txt
      - name: Assemble release evidence
        run: |
          mkdir -p release-evidence/artifacts release-evidence/consumer-poms release-evidence/signatures release-evidence/sboms
          find . -path '*/target/*.asc' -type f
          find . -path '*/target/*.jar' -type f ! -path '*/target/project-local-repo/*'
          cp central-deploy.log release-evidence/central-deploy.log
          printf 'source_commit=%s\n' "$GITHUB_SHA" > release-evidence/release-metadata.txt
      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          is_prerelease=false
          case "${RELEASE_VERSION}" in
            *-*) is_prerelease=true ;;
          esac
          release_flags=(--verify-tag)
          if [[ "${is_prerelease}" == "true" ]]; then
            release_flags+=(--prerelease --latest=false)
          fi
          gh release create "${{ inputs.release_tag }}" --verify-tag
          gh release edit "${{ inputs.release_tag }}" --draft=false --prerelease="${is_prerelease}"
YAML
assert_accepts "${complete_workflow}"

missing_project_local_repository_exclusion_workflow="${temp_dir}/missing-project-local-repository-exclusion-release.yml"
grep -v "project-local-repo" "${complete_workflow}" > "${missing_project_local_repository_exclusion_workflow}"
assert_rejects "${missing_project_local_repository_exclusion_workflow}"

legacy_version_extraction_workflow="${temp_dir}/legacy-version-extraction-release.yml"
awk '
    /          version="\$\(/ {
        print "          version=\"\$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)\""
        skipping = 1
        next
    }
    skipping && /          \)/ {
        skipping = 0
        next
    }
    !skipping { print }
' "${complete_workflow}" > "${legacy_version_extraction_workflow}"
assert_rejects "${legacy_version_extraction_workflow}"

partially_legacy_version_extraction_workflow="${temp_dir}/partially-legacy-version-extraction-release.yml"
awk '
    /          version="\$\(/ {
        version_extraction_count++
        if (version_extraction_count == 2) {
            print "          version=\"\$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)\""
            skipping = 1
            next
        }
    }
    skipping && /          \)/ {
        skipping = 0
        next
    }
    !skipping { print }
' "${complete_workflow}" > "${partially_legacy_version_extraction_workflow}"
assert_rejects "${partially_legacy_version_extraction_workflow}"

missing_consumer_pom_version_extraction_workflow="${temp_dir}/missing-consumer-pom-version-extraction-release.yml"
awk '
    /          version="\$\(/ {
        version_extraction_count++
        if (version_extraction_count == 2) {
            skipping = 1
            next
        }
    }
    skipping && /          \)/ {
        skipping = 0
        next
    }
    !skipping { print }
' "${complete_workflow}" > "${missing_consumer_pom_version_extraction_workflow}"
assert_rejects "${missing_consumer_pom_version_extraction_workflow}"

missing_vulnerability_alerts_permission_workflow="${temp_dir}/missing-vulnerability-alerts-permission-release.yml"
grep -v "vulnerability-alerts: read" "${complete_workflow}" > "${missing_vulnerability_alerts_permission_workflow}"
assert_rejects "${missing_vulnerability_alerts_permission_workflow}"

missing_contents_write_workflow="${temp_dir}/missing-contents-write-release.yml"
sed 's/contents: write/contents: read/' "${complete_workflow}" > "${missing_contents_write_workflow}"
assert_rejects "${missing_contents_write_workflow}"

missing_central_repository_workflow="${temp_dir}/missing-central-repository-release.yml"
grep -Ev 'repo\.maven\.apache\.org/maven2|jfoundry-parent-\$\{RELEASE_VERSION\}\.pom' "${complete_workflow}" > "${missing_central_repository_workflow}"
assert_rejects "${missing_central_repository_workflow}"

missing_github_release_workflow="${temp_dir}/missing-github-release.yml"
grep -v "Create GitHub Release\|gh release create\|--verify-tag" "${complete_workflow}" > "${missing_github_release_workflow}"
assert_rejects "${missing_github_release_workflow}"

missing_github_token_workflow="${temp_dir}/missing-github-token-release.yml"
grep -v 'GH_TOKEN: ${{ github.token }}' "${complete_workflow}" > "${missing_github_token_workflow}"
assert_rejects "${missing_github_token_workflow}"

missing_draft_publication_workflow="${temp_dir}/missing-draft-publication-release.yml"
grep -v "gh release edit\|--draft=false" "${complete_workflow}" > "${missing_draft_publication_workflow}"
assert_rejects "${missing_draft_publication_workflow}"

missing_prerelease_classification_workflow="${temp_dir}/missing-prerelease-classification-release.yml"
sed -e '/is_prerelease=false/d' \
    -e '/case "${RELEASE_VERSION}"/,/esac/d' \
    -e '/release_flags/d' \
    -e '/--prerelease --latest=false/d' \
    -e 's/ --prerelease="${is_prerelease}"//' \
    "${complete_workflow}" > "${missing_prerelease_classification_workflow}"
assert_rejects "${missing_prerelease_classification_workflow}"

missing_failure_evidence_workflow="${temp_dir}/missing-failure-evidence-release.yml"
grep -v "always()" "${complete_workflow}" > "${missing_failure_evidence_workflow}"
assert_rejects "${missing_failure_evidence_workflow}"

missing_main_ancestry_workflow="${temp_dir}/missing-main-ancestry-release.yml"
grep -v "git merge-base --is-ancestor\|origin/main" "${complete_workflow}" > "${missing_main_ancestry_workflow}"
assert_rejects "${missing_main_ancestry_workflow}"

stale_main_fetch_workflow="${temp_dir}/stale-main-fetch-release.yml"
sed 's/+refs\/heads\/main:refs\/remotes\/origin\/main/main/' "${complete_workflow}" > "${stale_main_fetch_workflow}"
assert_rejects "${stale_main_fetch_workflow}"

missing_central_status_classification_workflow="${temp_dir}/missing-central-status-classification-release.yml"
grep -v "200)\|404)\|Central publication lookup returned unexpected HTTP status" "${complete_workflow}" > "${missing_central_status_classification_workflow}"
assert_rejects "${missing_central_status_classification_workflow}"

missing_alert_workflow="${temp_dir}/missing-alert-release.yml"
grep -v "Verify Dependabot security alerts\|dependabot/alerts" "${safe_workflow}" > "${missing_alert_workflow}"
assert_rejects "${missing_alert_workflow}"

missing_ci_workflow="${temp_dir}/missing-ci-release.yml"
grep -v "Verify complete CI\|gh run view" "${safe_workflow}" > "${missing_ci_workflow}"
assert_rejects "${missing_ci_workflow}"

missing_consumer_pom_verification_workflow="${temp_dir}/missing-consumer-pom-verification-release.yml"
grep -v "Verify Maven Central Consumer POMs\|verify-consumer-pom.sh" "${complete_workflow}" > "${missing_consumer_pom_verification_workflow}"
assert_rejects "${missing_consumer_pom_verification_workflow}"

manual_publication_pom="${temp_dir}/manual-publication-pom.xml"
cat > "${manual_publication_pom}" <<'XML'
<project>
  <profiles>
    <profile>
      <id>release</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.sonatype.central</groupId>
            <artifactId>central-publishing-maven-plugin</artifactId>
            <configuration>
              <autoPublish>false</autoPublish>
              <waitUntil>VALIDATED</waitUntil>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
XML
if bash "${VERIFY_SCRIPT}" "${complete_workflow}" "${manual_publication_pom}" >/dev/null 2>&1; then
    echo "Expected release workflow verification to reject a manually published Central profile." >&2
    exit 1
fi

assert_accepts "${ROOT_DIR}/.github/workflows/release.yml"

echo "Release workflow verification tests passed."
