#!/usr/bin/env python3

"""Validate the repository's supply-chain workflow policy."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def load(path: Path, prefix: str) -> dict:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError:
        fail(f"{prefix}: could not safely parse YAML")
    if not isinstance(value, dict):
        fail(f"{prefix}: root must be a mapping")
    return value


def values(value: object) -> list[str]:
    if isinstance(value, dict):
        return [item for child in value.values() for item in values(child)]
    if isinstance(value, list):
        return [item for child in value for item in values(child)]
    return [str(value)]


def verify_ci(root: Path) -> None:
    config = load(root / ".github/workflows/ci.yml", ".github/workflows/ci.yml Documentation checks")
    jobs = config.get("jobs")
    steps = jobs.get("docs", {}).get("steps") if isinstance(jobs, dict) and isinstance(jobs.get("docs"), dict) else None
    required = ["bash scripts/verify-compatibility-matrix.sh", "bash scripts/verify-compatibility-matrix-test.sh"]
    if not isinstance(steps, list) or any(step.get("run") not in required for step in [] if isinstance(step, dict)):
        fail(f".github/workflows/ci.yml Documentation checks must run: {required[0]}")
    for command in required:
        if not any(isinstance(step, dict) and step.get("run") == command for step in steps):
            fail(f".github/workflows/ci.yml Documentation checks must run: {command}")


def verify_dependabot(root: Path) -> None:
    prefix = "Dependabot update policy is invalid"
    config = load(root / ".github/dependabot.yml", prefix)
    updates = config.get("updates")
    if not isinstance(updates, list):
        fail(f"{prefix}: updates must be an array")
    if any(not isinstance(update, dict) for update in updates):
        fail(f"{prefix}: each updates entry must be a mapping")
    maven = [update for update in updates if update.get("package-ecosystem") == "maven"]
    if len(maven) != 1:
        fail(f"{prefix}: must contain exactly one Maven updates entry")
    expected_cooldown = {
        "default-days": 1, "semver-major-days": 7, "semver-minor-days": 7, "semver-patch-days": 1,
        "include": ["org.springframework.boot:spring-boot-dependencies", "org.springframework.boot:spring-boot-starter-parent", "org.springframework.boot:spring-boot-maven-plugin", "io.quarkus.platform:quarkus-bom", "io.quarkus:quarkus-extension-maven-plugin", "io.quarkus:quarkus-extension-processor", "io.quarkus:quarkus-maven-plugin"],
        "exclude": ["org.springframework.boot:spring-boot-dependencies", "org.springframework.boot:spring-boot-starter-parent", "org.springframework.boot:spring-boot-maven-plugin"],
    }
    if maven[0].get("cooldown") != expected_cooldown:
        expected_inspect = '{"default-days"=>1, "semver-major-days"=>7, "semver-minor-days"=>7, "semver-patch-days"=>1, "include"=>["org.springframework.boot:spring-boot-dependencies", "org.springframework.boot:spring-boot-starter-parent", "org.springframework.boot:spring-boot-maven-plugin", "io.quarkus.platform:quarkus-bom", "io.quarkus:quarkus-extension-maven-plugin", "io.quarkus:quarkus-extension-processor", "io.quarkus:quarkus-maven-plugin"], "exclude"=>["org.springframework.boot:spring-boot-dependencies", "org.springframework.boot:spring-boot-starter-parent", "org.springframework.boot:spring-boot-maven-plugin"]}'
        fail(f"{prefix}: Maven updates must use the {expected_inspect} cooldown")
    groups = maven[0].get("groups")
    if not isinstance(groups, dict):
        fail(f"{prefix}: Maven groups must be a mapping")
    expected_groups = {
        "jfoundry-spring-boot-platform": {"patterns": expected_cooldown["include"][:3], "update-types": ["patch", "minor"]},
        "jfoundry-quarkus-platform": {"patterns": expected_cooldown["include"][3:], "update-types": ["patch", "minor"]},
        "jfoundry-maven-patches": {"patterns": ["*"], "update-types": ["patch"]},
    }
    if list(groups) != list(expected_groups):
        fail(f"{prefix}: Maven groups must be ordered as {', '.join(expected_groups)}")
    for name, expected in expected_groups.items():
        if groups.get(name) != expected:
            if name == "jfoundry-spring-boot-platform":
                fail(f"{prefix}: jfoundry-spring-boot-platform must group the complete supported coordinate set for patch and minor updates")
            if name == "jfoundry-quarkus-platform":
                fail(f"{prefix}: jfoundry-quarkus-platform must group the complete supported coordinate set for patch and minor updates")
            fail(f"{prefix}: jfoundry-maven-patches must group all remaining patch updates")
    if "ignore" in maven[0]:
        fail(f"{prefix}: Maven updates must not define ignore rules")
    actions = [update for update in updates if update.get("package-ecosystem") == "github-actions"]
    if len(actions) != 1 or actions[0].get("groups") != {"github-codeql-action": {"patterns": ["github/codeql-action/*"]}}:
        fail(f"{prefix}: GitHub Actions groups are invalid")


def verify_auto_merge(root: Path) -> None:
    prefix = "Dependabot auto-merge workflow is invalid"
    workflow = load(root / ".github/workflows/auto-merge-dependabot.yml", prefix)
    events = workflow.get(True, workflow.get("on"))
    if not isinstance(events, dict) or list(events) != ["pull_request_target"]:
        fail(f"{prefix}: must run only on pull_request_target")
    if workflow.get("permissions") != {"contents": "write", "pull-requests": "write"}:
        fail(f"{prefix}: permissions must be exactly {{'contents': 'write', 'pull-requests': 'write'}}")
    if any(re.search(r"\bsecrets\s*(?:\.|\[)", item) for item in values(workflow)):
        fail(f"{prefix}: must not use secrets")
    jobs = workflow.get("jobs")
    job = jobs.get("enable-auto-merge") if isinstance(jobs, dict) else None
    if not isinstance(job, dict):
        fail(f"{prefix}: must define enable-auto-merge")
    if " ".join(str(job.get("if", "")).split()) != "github.event.pull_request.user.login == 'dependabot[bot]' && github.event.pull_request.base.ref == 'main'":
        fail(f"{prefix}: must guard Dependabot PRs targeting main")
    steps = job.get("steps")
    if not isinstance(steps, list):
        fail(f"{prefix}: enable-auto-merge steps must be an array")
    for candidate in jobs.values():
        if not isinstance(candidate, dict):
            fail(f"{prefix}: each job must be a mapping")
        if "permissions" in candidate:
            fail(f"{prefix}: jobs must not override permissions")
        for step in candidate.get("steps", []) if isinstance(candidate.get("steps"), list) else []:
            if not isinstance(step, dict):
                fail(f"{prefix}: each step must be a mapping")
            if re.search("checkout", str(step.get("uses", "")), re.I) or re.search(r"\bcheckout\b", str(step.get("run", "")), re.I):
                fail(f"{prefix}: must not check out code")
    by_id = {identifier: [index for index, step in enumerate(steps) if isinstance(step, dict) and step.get("id") == identifier] for identifier in ["scope", "dependabot-metadata", "eligibility", "dependency_policy"]}
    for identifier, expected in [("scope", 1), ("dependabot-metadata", 1), ("eligibility", 1), ("dependency_policy", 0)]:
        if len(by_id[identifier]) != expected:
            fail(f"{prefix}: invalid {identifier} step count")
    scope_index, metadata_index, eligibility_index = by_id["scope"][0], by_id["dependabot-metadata"][0], by_id["eligibility"][0]
    scope, metadata, eligibility = steps[scope_index], steps[metadata_index], steps[eligibility_index]
    condition = "steps.scope.outputs.is_maven_update == 'true'"
    if 'gh api "repos/${REPOSITORY}/pulls/${PR_NUMBER}/files"' not in str(scope.get("run", "")) or "grep -Evq '(^|/)pom\\.xml$'" not in str(scope.get("run", "")):
        fail(f"{prefix}: scope must inspect only Maven POM files")
    if not metadata_index > scope_index or metadata.get("uses") != "dependabot/fetch-metadata@25dd0e34f4fe68f24cc83900b1fe3fe149efef98" or metadata.get("if") != condition or metadata.get("with", {}).get("github-token") != "${{ github.token }}":
        fail(f"{prefix}: metadata step is invalid")
    if not eligibility_index > metadata_index or eligibility.get("if") != condition or eligibility.get("env", {}).get("UPDATE_TYPE") != "${{ steps.dependabot-metadata.outputs.update-type }}":
        fail(f"{prefix}: eligibility step is invalid")
    eligibility_run = str(eligibility.get("run", ""))
    if any(required not in eligibility_run for required in ["version-update:semver-patch", "is_patch_update=true", "is_patch_update=false"]):
        fail(f"{prefix}: eligibility must allow only semantic patch updates")
    merges = [index for index, step in enumerate(steps) if re.search(r"\bgh\s+pr\s+merge\b", str(step.get("run", "")))]
    if len(merges) != 1:
        fail(f"{prefix}: must contain exactly one gh pr merge step")
    merge = steps[merges[0]]
    if merges[0] <= eligibility_index or " ".join(str(merge.get("if", "")).split()) != "steps.scope.outputs.is_maven_update == 'true' && steps.eligibility.outputs.is_patch_update == 'true'":
        fail(f"{prefix}: merge must require Maven-only scope and patch eligibility")
    run = str(merge.get("run", ""))
    if not re.search(r'--repo\s+"?\$\{REPOSITORY\}"?', run) or not re.search(r"(?:^|\s)--auto(?:\s|$)", run) or not re.search(r"(?:^|\s)--rebase(?:\s|$)", run) or merge.get("env", {}).get("REPOSITORY") != "${{ github.repository }}":
        fail(f"{prefix}: merge command is invalid")


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").expanduser().resolve()
    required_files = [".github/dependabot.yml", ".github/workflows/codeql.yml", ".github/workflows/release.yml", ".github/workflows/ci.yml", ".github/workflows/snapshot.yml", ".github/workflows/prepare-snapshot.yml", ".github/workflows/auto-merge-dependabot.yml"]
    for relative in required_files:
        if not (root / relative).is_file():
            fail(f"Supply-chain configuration is missing: {relative}")
    required_text = {
        ".github/dependabot.yml": ["package-ecosystem: maven", "package-ecosystem: github-actions"],
        ".github/workflows/codeql.yml": ["security-events: write", "github/codeql-action/init", "github/codeql-action/analyze", "language: java-kotlin", "build-mode: manual", "language: actions", "build-mode: none", "build-mode: ${{ matrix.build-mode }}", "actions/setup-java", "java-version: 25", "-pl '!jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests'"],
        ".github/workflows/ci.yml": ["name: Dependency Review", "actions/dependency-review-action", "fail-on-severity: high", "needs.dependency-review.result", "Test Consumer POM verification", "bash scripts/verify-consumer-pom-test.sh", "Verify release POM metadata", "bash scripts/verify-release-pom-metadata.sh", "bash scripts/verify-maven-4-model.sh", "bash scripts/verify-maven-4-model-test.sh", "bash scripts/set-maven-reactor-version-test.sh", "bash scripts/verify-release-pom-metadata-test.sh", "Verify reactor Consumer POMs", '-Dmaven.repo.local="${consumer_pom_repository}" install', 'bash scripts/verify-consumer-pom.sh "${consumer_pom_repository}" "${version}"', '"$(pwd)/mvnw"', "bash scripts/verify-dependency-boundaries.sh", "bash scripts/verify-dependency-boundaries-test.sh"],
        ".github/workflows/release.yml": ["actions/upload-artifact", "release-evidence"],
        ".github/workflows/snapshot.yml": ["sed -n 's/^\\[INFO\\] \\[stdout\\] //p'", "is_snapshot=true", "if: steps.version.outputs.is_snapshot == 'true'"],
        ".github/workflows/prepare-snapshot.yml": ["workflow_run:", "workflows:", "- Release", "git tag --points-at", "contents: write", "pull-requests: write", "scripts/set-maven-reactor-version.py", "git push --set-upstream origin", "gh pr create"],
    }
    verify_ci(root)
    for relative, texts in required_text.items():
        source = (root / relative).read_text(encoding="utf-8")
        for required in texts:
            if required not in source:
                fail(f"{relative} must contain: {required}")
    ci_source = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    verify_dependabot(root)
    verify_auto_merge(root)
    if "command -v mvn" in ci_source or "Apache Maven 3." in ci_source:
        fail(".github/workflows/ci.yml must not contain an unsupported Maven 3 source-build check")
    prepare_source = (root / ".github/workflows/prepare-snapshot.yml").read_text(encoding="utf-8")
    if "versions:set" in prepare_source or "jfoundry-boms/jfoundry-spring-cloud-parent/pom.xml" in prepare_source:
        fail("prepare-snapshot.yml contains a forbidden release mutation")
    snapshot_source = (root / ".github/workflows/snapshot.yml").read_text(encoding="utf-8")
    if "-DforceStdout | tail -n 1" in snapshot_source:
        fail(".github/workflows/snapshot.yml must not use bare Maven 4 version extraction")
    print(f"Supply-chain workflow verification passed: {root}")


if __name__ == "__main__":
    main()
