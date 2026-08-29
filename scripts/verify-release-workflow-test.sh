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
  attestations: write
  id-token: write
jobs:
  publish:
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ inputs.release_tag }}
      - name: Verify release workflow source
        run: test "${GITHUB_REF}" = "refs/heads/main"
      - name: Verify immutable release source
        run: |
          version="$(
            ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout |
              sed -n 's/^\[INFO\] \[stdout\] //p' |
              tail -n 1
          )"
          test "${{ inputs.release_tag }}" = "v${version}"
          test -z "$(git status --porcelain)"
          bash scripts/verify-release-pom-metadata.sh
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
      - name: Install Apache Maven 3 for Central publication
        id: maven_3
        env:
          MAVEN_3_VERSION: 3.9.16
          MAVEN_3_SHA512: 831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6
        run: |
          archive="${RUNNER_TEMP}/apache-maven-${MAVEN_3_VERSION}-bin.tar.gz"
          curl --fail --location --retry 3 --retry-all-errors --output "${archive}" \
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_3_VERSION}/apache-maven-${MAVEN_3_VERSION}-bin.tar.gz"
          printf '%s  %s\n' "${MAVEN_3_SHA512}" "${archive}" | sha512sum --check --status
          tar -xzf "${archive}" -C "${RUNNER_TEMP}"
          executable="${RUNNER_TEMP}/apache-maven-${MAVEN_3_VERSION}/bin/mvn"
          echo "executable=${executable}" >> "${GITHUB_OUTPUT}"
      - name: Prepare Maven 3 publication tree
        run: |
          publication_tree="${RUNNER_TEMP}/jfoundry-maven3-publication-tree"
          bash scripts/generate-maven3-publication-tree.sh "${publication_tree}"
          echo "PUBLICATION_EVIDENCE_ROOT=${publication_tree}" >> "${GITHUB_ENV}"
      - name: Publish Maven Central deployment
        if: steps.central_publication.outputs.already_published != 'true'
        run: |
          publication_tree="${RUNNER_TEMP}/jfoundry-maven3-publication-tree"
          (
            cd "${publication_tree}"
            "${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests deploy \
              "-DaltDeploymentRepository=jfoundry::file:${RUNNER_TEMP}/jfoundry-release-deployment" \
              | tee "${GITHUB_WORKSPACE}/central-deploy.log"
          )
          deployment_id="$(sed -nE 's/.*deploymentId: ([[:alnum:]-]+).*/\1/p' central-deploy.log | tail -n 1)"
          if [[ -z "${deployment_id}" ]]; then
            echo "Central Publishing did not report a deploymentId." >&2
            exit 1
          fi
      - name: Rebuild existing release evidence with Maven 3
        if: steps.central_publication.outputs.already_published == 'true'
        run: |
          publication_tree="${RUNNER_TEMP}/jfoundry-maven3-publication-tree"
          (
            cd "${publication_tree}"
            "${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests verify
          )
      - name: Check Maven Central publication
        id: central_publication
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
          artifact_root="${PUBLICATION_EVIDENCE_ROOT}"
          mkdir -p release-evidence/artifacts release-evidence/consumer-poms release-evidence/signatures release-evidence/sboms
          find "${artifact_root}" -path '*/target/*.asc' -type f
          find "${artifact_root}" -path '*/target/*.jar' -type f ! -path '*/target/project-local-repo/*'
          find "${artifact_root}" -path '*/target/bom.*' -type f ! -path '*/target/project-local-repo/*'
          cp central-deploy.log release-evidence/central-deploy.log
          printf 'source_commit=%s\n' "$GITHUB_SHA" > release-evidence/release-metadata.txt
          echo "artifact_evidence_root=${artifact_root}" >> release-evidence/release-metadata.txt
      - name: Archive release evidence
        run: tar -czf release-evidence.tar.gz release-evidence
      - name: Attest release artifact provenance
        uses: actions/attest-build-provenance@v2
        with:
          subject-path: release-evidence.tar.gz
      - name: Create GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_TAG: ${{ inputs.release_tag }}
        run: |
          is_prerelease=false
          case "${RELEASE_VERSION}" in
            *-*) is_prerelease=true ;;
          esac
          release_flags=(--verify-tag)
          if [[ "${is_prerelease}" == "true" ]]; then
            release_flags+=(--prerelease --latest=false)
          fi
          gh release create "${RELEASE_TAG}" --verify-tag --title "${RELEASE_TAG}"
          gh release edit "${RELEASE_TAG}" --title "${RELEASE_TAG}" --draft=false --prerelease="${is_prerelease}"
YAML
assert_accepts "${complete_workflow}"

missing_publication_evidence_root_workflow="${temp_dir}/missing-publication-evidence-root-release.yml"
grep -v 'PUBLICATION_EVIDENCE_ROOT' "${complete_workflow}" > "${missing_publication_evidence_root_workflow}"
assert_rejects "${missing_publication_evidence_root_workflow}"

workspace_only_evidence_workflow="${temp_dir}/workspace-only-evidence-release.yml"
sed 's/artifact_root="${PUBLICATION_EVIDENCE_ROOT}"/artifact_root="${GITHUB_WORKSPACE}"/' \
    "${complete_workflow}" > "${workspace_only_evidence_workflow}"
assert_rejects "${workspace_only_evidence_workflow}"

missing_maven3_publication_tree_workflow="${temp_dir}/missing-maven3-publication-tree-release.yml"
grep -v 'publication_tree=\|generate-maven3-publication-tree.sh\|cd "${publication_tree}"' \
    "${complete_workflow}" > "${missing_maven3_publication_tree_workflow}"
assert_rejects "${missing_maven3_publication_tree_workflow}"

conditional_maven3_install_workflow="${temp_dir}/conditional-maven3-install-release.yml"
awk '{ print; if ($0 ~ /id: maven_3/) print "        if: steps.central_publication.outputs.already_published != '\''true'\''" }' \
    "${complete_workflow}" > "${conditional_maven3_install_workflow}"
assert_rejects "${conditional_maven3_install_workflow}"

conditional_publication_tree_workflow="${temp_dir}/conditional-publication-tree-release.yml"
awk '{ print; if ($0 ~ /name: Prepare Maven 3 publication tree/) print "        if: steps.central_publication.outputs.already_published != '\''true'\''" }' \
    "${complete_workflow}" > "${conditional_publication_tree_workflow}"
assert_rejects "${conditional_publication_tree_workflow}"

missing_existing_release_rebuild_workflow="${temp_dir}/missing-existing-release-rebuild-release.yml"
sed '/name: Rebuild existing release evidence with Maven 3/,/^[[:space:]]*- name:/ { /name: Rebuild existing release evidence with Maven 3/d; /already_published == '\''true'\''/d; /publication_tree=/d; /cd "${publication_tree}"/d; /steps.maven_3.outputs.executable.*verify/d; }' \
    "${complete_workflow}" > "${missing_existing_release_rebuild_workflow}"
assert_rejects "${missing_existing_release_rebuild_workflow}"

publish_outside_publication_tree_workflow="${temp_dir}/publish-outside-publication-tree-release.yml"
sed '/name: Publish Maven Central deployment/,/name: Rebuild existing release evidence with Maven 3/ s/cd "${publication_tree}"/cd "${GITHUB_WORKSPACE}"/' \
    "${complete_workflow}" > "${publish_outside_publication_tree_workflow}"
assert_rejects "${publish_outside_publication_tree_workflow}"

rebuild_outside_publication_tree_workflow="${temp_dir}/rebuild-outside-publication-tree-release.yml"
sed '/name: Rebuild existing release evidence with Maven 3/,/name: Check Maven Central publication/ s/cd "${publication_tree}"/cd "${GITHUB_WORKSPACE}"/' \
    "${complete_workflow}" > "${rebuild_outside_publication_tree_workflow}"
assert_rejects "${rebuild_outside_publication_tree_workflow}"

overridden_publication_evidence_root_workflow="${temp_dir}/overridden-publication-evidence-root-release.yml"
awk '{ print; if ($0 ~ /artifact_root="\$\{PUBLICATION_EVIDENCE_ROOT\}"/) print "          artifact_root=\"${GITHUB_WORKSPACE}\"" }' \
    "${complete_workflow}" > "${overridden_publication_evidence_root_workflow}"
assert_rejects "${overridden_publication_evidence_root_workflow}"

non_main_workflow_source_workflow="${temp_dir}/non-main-workflow-source-release.yml"
grep -v 'test "${GITHUB_REF}" = "refs/heads/main"' "${complete_workflow}" > "${non_main_workflow_source_workflow}"
assert_rejects "${non_main_workflow_source_workflow}"

maven4_direct_publish_workflow="${temp_dir}/maven4-direct-publish-release.yml"
sed 's#"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests deploy#./mvnw -B -Prelease -DskipTests verify org.sonatype.central:central-publishing-maven-plugin:0.11.0:publish#' \
    "${complete_workflow}" > "${maven4_direct_publish_workflow}"
assert_rejects "${maven4_direct_publish_workflow}"

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

unsupported_vulnerability_alerts_permission_workflow="${temp_dir}/unsupported-vulnerability-alerts-permission-release.yml"
sed '/security-events: read/a\
  vulnerability-alerts: read' "${complete_workflow}" > "${unsupported_vulnerability_alerts_permission_workflow}"
assert_rejects "${unsupported_vulnerability_alerts_permission_workflow}"

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

missing_release_title_workflow="${temp_dir}/missing-release-title.yml"
sed 's/ --title "${RELEASE_TAG}"//g' "${complete_workflow}" > "${missing_release_title_workflow}"
assert_rejects "${missing_release_title_workflow}"

prefixed_release_title_workflow="${temp_dir}/prefixed-release-title.yml"
sed 's/--title "${RELEASE_TAG}"/--title "JFoundry ${RELEASE_TAG}"/g' \
    "${complete_workflow}" > "${prefixed_release_title_workflow}"
assert_rejects "${prefixed_release_title_workflow}"

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

missing_release_pom_metadata_workflow="${temp_dir}/missing-release-pom-metadata-release.yml"
grep -v "verify-release-pom-metadata.sh" "${complete_workflow}" > "${missing_release_pom_metadata_workflow}"
assert_rejects "${missing_release_pom_metadata_workflow}"

misplaced_release_pom_metadata_workflow="${temp_dir}/misplaced-release-pom-metadata-release.yml"
ruby - "${complete_workflow}" "${misplaced_release_pom_metadata_workflow}" <<'RUBY'
source, target = ARGV
content = File.read(source)
metadata_call = "          bash scripts/verify-release-pom-metadata.sh\n"
abort "Expected release POM metadata call" unless content.sub!(metadata_call, "")
File.write(target, "#{content}#{metadata_call}")
RUBY
assert_rejects "${misplaced_release_pom_metadata_workflow}"

directory_provenance_subject_workflow="${temp_dir}/directory-provenance-subject-release.yml"
sed 's#subject-path: release-evidence.tar.gz#subject-path: release-evidence/\*\*#' \
    "${ROOT_DIR}/.github/workflows/release.yml" > "${directory_provenance_subject_workflow}"
assert_rejects "${directory_provenance_subject_workflow}"

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
