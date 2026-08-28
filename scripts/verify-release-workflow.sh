#!/usr/bin/env bash

set -euo pipefail

workflow_file="${1:-.github/workflows/release.yml}"
pom_file="${2:-pom.yaml}"
bom_pom_files=(
    "jfoundry-boms/jfoundry-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-foundation-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-modules-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-quarkus-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-spring-boot-dependencies/pom.yaml"
    "jfoundry-boms/jfoundry-spring-cloud-dependencies/pom.yaml"
)

if [[ ! -f "${workflow_file}" ]]; then
    echo "Release workflow does not exist: ${workflow_file}" >&2
    exit 1
fi

if [[ ! -f "${pom_file}" ]]; then
    echo "Release Maven configuration does not exist: ${pom_file}" >&2
    exit 1
fi

require_text() {
    local text="$1"
    if ! grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Release workflow must contain: ${text}" >&2
        exit 1
    fi
}

require_count() {
    local text="$1"
    local expected_count="$2"
    local actual_count

    actual_count="$(grep -Fc -- "${text}" "${workflow_file}" || true)"
    if [[ "${actual_count}" != "${expected_count}" ]]; then
        echo "Release workflow must contain ${expected_count} occurrence(s) of: ${text}" >&2
        exit 1
    fi
}

require_automatic_central_publication() {
    local build_file="$1"

    ruby - "${build_file}" <<'RUBY'
require "rexml/document"
require "yaml"

path = ARGV.fetch(0)

if File.extname(path) == ".yaml"
  project = YAML.safe_load(File.read(path), aliases: true)
  release = Array(project["profiles"]).find { |profile| profile["id"] == "release" }
  plugins = Array(release&.dig("build", "plugins"))
  central = plugins.find do |plugin|
    plugin["groupId"] == "org.sonatype.central" &&
      plugin["artifactId"] == "central-publishing-maven-plugin"
  end
  configuration = central&.fetch("configuration", nil)
  valid = [true, "true"].include?(configuration&.fetch("autoPublish", nil)) &&
    configuration&.fetch("waitUntil", nil) == "PUBLISHED"
else
  project = REXML::Document.new(File.read(path)).root
  release = project.elements.to_a("profiles/profile").find do |profile|
    profile.elements["id"]&.text.to_s.strip == "release"
  end
  central = release&.elements&.to_a("build/plugins/plugin")&.find do |plugin|
    plugin.elements["groupId"]&.text.to_s.strip == "org.sonatype.central" &&
      plugin.elements["artifactId"]&.text.to_s.strip == "central-publishing-maven-plugin"
  end
  configuration = central&.elements&.[]("configuration")
  valid = configuration&.elements&.[]("autoPublish")&.text.to_s.strip == "true" &&
    configuration&.elements&.[]("waitUntil")&.text.to_s.strip == "PUBLISHED"
end

unless valid
  warn "Release Maven configuration must publish automatically and wait until PUBLISHED: #{path}"
  exit 1
end
RUBY
}

require_publication_evidence_flow() {
    ruby - "${workflow_file}" <<'RUBY'
require "yaml"

path = ARGV.fetch(0)
workflow = YAML.safe_load(File.read(path), aliases: true)
steps = workflow.dig("jobs", "publish", "steps")
unless steps.is_a?(Array)
  warn "Release workflow must define jobs.publish.steps"
  exit 1
end

step = lambda do |name|
  value = steps.find { |candidate| candidate.is_a?(Hash) && candidate["name"] == name }
  unless value
    warn "Release workflow must define step: #{name}"
    exit 1
  end
  value
end

install = step.call("Install Apache Maven 3 for Central publication")
if install.key?("if")
  warn "Maven 3 installation must run for new and already-published releases"
  exit 1
end

prepare = step.call("Prepare Maven 3 publication tree")
if prepare.key?("if")
  warn "Maven 3 publication tree preparation must run for new and already-published releases"
  exit 1
end
prepare_run = prepare.fetch("run", "")
unless prepare_run.include?('bash scripts/generate-maven3-publication-tree.sh "${publication_tree}"') &&
    prepare_run.include?('echo "PUBLICATION_EVIDENCE_ROOT=${publication_tree}" >> "${GITHUB_ENV}"')
  warn "Maven 3 publication tree preparation must export the publication evidence root"
  exit 1
end

publish = step.call("Publish Maven Central deployment")
publish_run = publish.fetch("run", "")
unless publish["if"] == "steps.central_publication.outputs.already_published != 'true'" &&
    publish_run.include?('cd "${publication_tree}"') &&
    publish_run.include?('"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests deploy')
  warn "New releases must deploy from the Maven 3 publication tree"
  exit 1
end

rebuild = step.call("Rebuild existing release evidence with Maven 3")
rebuild_run = rebuild.fetch("run", "")
unless rebuild["if"] == "steps.central_publication.outputs.already_published == 'true'" &&
    rebuild_run.include?('cd "${publication_tree}"') &&
    rebuild_run.include?('"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests verify')
  warn "Already-published releases must rebuild evidence from the Maven 3 publication tree"
  exit 1
end

evidence = step.call("Assemble release evidence")
artifact_root_assignments = evidence.fetch("run", "").lines.map(&:strip).select do |line|
  line.start_with?("artifact_root=")
end
unless artifact_root_assignments == ['artifact_root="${PUBLICATION_EVIDENCE_ROOT}"']
  warn "Release evidence must be collected exclusively from the Maven 3 publication tree"
  exit 1
end
RUBY
}

forbid_text() {
    local text="$1"
    if grep -Fq -- "${text}" "${workflow_file}"; then
        echo "Release workflow must not contain: ${text}" >&2
        exit 1
    fi
}

require_text "workflow_dispatch:"
require_text "release_tag:"
require_text 'ref: ${{ inputs.release_tag }}'
require_text 'test "${GITHUB_REF}" = "refs/heads/main"'
require_text "Verify immutable release source"
require_text "Verify complete CI"
require_text 'test "${{ inputs.release_tag }}" = "v${version}"'
require_text 'test -z "$(git status --porcelain)"'
require_text "bash scripts/verify-release-pom-metadata.sh"
require_count "sed -n 's/^\\[INFO\\] \\[stdout\\] //p'" 2
require_text "gh run view"
require_text "actions: read"
require_text "contents: write"
require_text 'GH_TOKEN: ${{ github.token }}'
require_text "-Prelease -DskipTests verify"
require_text "Verify Maven Central Consumer POMs"
require_text "verify-consumer-pom.sh"
require_text "Install Apache Maven 3 for Central publication"
require_text "Prepare Maven 3 publication tree"
require_text "MAVEN_3_VERSION: 3.9.16"
require_text "MAVEN_3_SHA512:"
require_text 'publication_tree="${RUNNER_TEMP}/jfoundry-maven3-publication-tree"'
require_text 'bash scripts/generate-maven3-publication-tree.sh "${publication_tree}"'
require_text 'echo "PUBLICATION_EVIDENCE_ROOT=${publication_tree}" >> "${GITHUB_ENV}"'
require_text 'cd "${publication_tree}"'
require_text '"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests deploy'
require_text "Rebuild existing release evidence with Maven 3"
require_text '"${{ steps.maven_3.outputs.executable }}" -B -T 1 -Prelease -DskipTests verify'
require_text '-DaltDeploymentRepository=jfoundry::file:${RUNNER_TEMP}/jfoundry-release-deployment'
require_text 'tee "${GITHUB_WORKSPACE}/central-deploy.log"'
require_text "deployment_id="
require_text "deploymentId: ([[:alnum:]-]+)"
require_text 'if [[ -z "${deployment_id}" ]]'
require_text "Check Maven Central publication"
require_text "Verify Maven Central publication"
require_text "repo.maven.apache.org/maven2"
require_text 'jfoundry-parent-${RELEASE_VERSION}.pom'
require_text "git merge-base --is-ancestor"
require_text "origin/main"
require_text "git fetch origin +refs/heads/main:refs/remotes/origin/main --no-tags"
require_text 'central_status="$('
require_text "--write-out '%{http_code}'"
require_text "200)"
require_text "404)"
require_text "Central publication lookup returned unexpected HTTP status"
require_text "Verify Dependabot security alerts"
require_text "dependabot/alerts"
require_text "security-events: read"
require_text "vulnerability-alerts: read"
require_text "attestations: write"
require_text "id-token: write"
require_text "actions/attest-build-provenance"
require_text "central-deploy.log"
require_text "Central Publishing did not report a deploymentId."
require_text "release-evidence/consumer-poms"
require_text "release-evidence/signatures"
require_text "release-metadata.txt"
require_text 'artifact_root="${PUBLICATION_EVIDENCE_ROOT}"'
require_text 'find "${artifact_root}" -path '\''*/target/*.jar'\'''
require_text 'find "${artifact_root}" -path '\''*/target/*.asc'\'''
require_text 'find "${artifact_root}" -path '\''*/target/bom.*'\'''
require_text 'artifact_evidence_root=${artifact_root}'
require_text "target/*.asc"
require_text "! -path '*/target/project-local-repo/*'"
require_text "central-deployment.txt"
require_text "Archive release evidence"
require_text "tar -czf release-evidence.tar.gz release-evidence"
require_text "subject-path: release-evidence.tar.gz"
require_text "gh release create"
require_text "gh release edit"
require_count '--title "${RELEASE_TAG}"' 2
require_text "--verify-tag"
require_text "--draft=false"
require_text "is_prerelease=false"
require_text "--prerelease"
require_text "--latest=false"
require_text "always()"

require_automatic_central_publication "${pom_file}"
require_publication_evidence_flow
for bom_pom_file in "${bom_pom_files[@]}"; do
    if [[ ! -f "${bom_pom_file}" ]]; then
        echo "Standalone BOM release Maven configuration does not exist: ${bom_pom_file}" >&2
        exit 1
    fi
    require_automatic_central_publication "${bom_pom_file}"
done

immutable_source_line="$(grep -n -F "Verify immutable release source" "${workflow_file}" | head -n 1 | cut -d: -f1)"
release_pom_metadata_line="$(grep -n -F "bash scripts/verify-release-pom-metadata.sh" "${workflow_file}" | head -n 1 | cut -d: -f1)"
complete_ci_line="$(grep -n -F "Verify complete CI" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${immutable_source_line}" || -z "${release_pom_metadata_line}" || -z "${complete_ci_line}" ||
      "${release_pom_metadata_line}" -le "${immutable_source_line}" ||
      "${release_pom_metadata_line}" -ge "${complete_ci_line}" ]]; then
    echo "Release POM metadata must be verified as part of the immutable release source step." >&2
    exit 1
fi

central_verification_line="$(grep -n -F "Verify Maven Central publication" "${workflow_file}" | head -n 1 | cut -d: -f1)"
github_release_line="$(grep -n -F "Create GitHub Release" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${central_verification_line}" || -z "${github_release_line}" || "${github_release_line}" -le "${central_verification_line}" ]]; then
    echo "GitHub Release creation must follow Maven Central publication verification." >&2
    exit 1
fi

forbid_text "versions-maven-plugin"
forbid_text "versions:set"
forbid_text "git push"
forbid_text "org.sonatype.central:central-publishing-maven-plugin:0.11.0:publish"
forbid_text "./mvnw -B -Prelease -DskipTests verify \\"
forbid_text "-DforceStdout | tail -n 1"
forbid_text "search.maven.org/solrsearch/select"
forbid_text "subject-path: release-evidence/**"
forbid_text '--title "JFoundry '

release_evidence_archive_line="$(grep -n -F "Archive release evidence" "${workflow_file}" | head -n 1 | cut -d: -f1)"
provenance_line="$(grep -n -F "Attest release artifact provenance" "${workflow_file}" | head -n 1 | cut -d: -f1)"
if [[ -z "${release_evidence_archive_line}" || -z "${provenance_line}" || "${provenance_line}" -le "${release_evidence_archive_line}" ]]; then
    echo "Release evidence must be archived before provenance attestation." >&2
    exit 1
fi

echo "Release workflow verification passed: ${workflow_file}"
