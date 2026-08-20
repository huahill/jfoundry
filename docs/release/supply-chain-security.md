# Supply Chain Security

JFoundry protects its delivery path from reviewed source to Maven Central artifact. These controls
do not replace an application's authentication, authorization, secret management, or production
operations.

## Automated Controls

- Dependabot monitors Maven and GitHub Actions dependencies and proposes updates weekly.
- Dependency Review blocks pull requests that add a High or Critical known vulnerability, or one of
  the prohibited copyleft licenses: AGPL-3.0, GPL-2.0-only, GPL-3.0-only, and SSPL-1.0.
- CodeQL analyzes Java/Kotlin source and GitHub Actions workflows on pull requests, `main`, and a
  weekly schedule.
- External GitHub Actions are pinned to commit SHAs. Dependabot owns updates to those pins.
- The release build generates an aggregate CycloneDX SBOM for compile, runtime, provided, and system
  dependencies, excluding test dependencies.
- A release is refused when GitHub reports an open High or Critical Dependabot alert. The release
  workflow archives JARs, SBOMs, SHA-256 checksums, and GitHub build provenance.
- Maven Central artifacts are signed with the release GPG key. The protected release workflow waits
  for Central publication and independently verifies public Maven Central artifact resolution before
  creating the corresponding GitHub Release.

Dependency Review evaluates dependency changes in a pull request. Dependabot and the release check
cover vulnerabilities that become known after the dependency was introduced. A false-positive or
exception requires a documented maintainer decision linked from the affected pull request or release
notes; it must state the advisory, affected artifact, reason, compensating control, and expiry date.

## Dependabot Update Governance

- Dependabot groups Maven patch updates. Minor and major Maven library updates remain individual pull
  requests for focused review and compatibility verification. Maven updates may be queued for rebase
  auto-merge only after Dependency Review and the `Merge gate` succeed.
- Dependabot does not ignore runtime platform dependency-management updates. Spring Boot, Spring
  Cloud, Spring Cloud Alibaba, Quarkus, and Helidon updates follow the same Maven policy as other
  dependencies, with the complete supported runtime matrix and `Merge gate` providing the
  compatibility boundary.
- CodeQL `init` and `analyze` updates are atomic: they are upgraded together in the same pull request.
  GitHub Actions updates, including CodeQL updates, are never eligible for Maven auto-merge.

## Required Repository Settings

Repository administrators must enable all of the following in GitHub repository security settings:

1. Dependency Graph, Dependabot alerts, and Dependabot security updates.
2. Secret scanning and push protection.
3. GitHub Actions full-length SHA pinning after verifying every workflow is pinned.
4. The `jfoundry` deployment environment with Maven Central and GPG secrets, plus release reviewers
   selected by the maintainers.
5. An active tag ruleset for `refs/tags/v*` that prohibits deletion and updates without bypass actors.
6. The active main-branch ruleset that requires the `Merge gate` status check. `Merge gate` requires
   the CI-integrated Dependency Review job for every pull request.

The release environment must contain `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, and
`GPG_PASSPHRASE`. Those values must never be committed, printed, or copied into issue discussions.

## Release Evidence

For every stable release and release candidate, retain the immutable Git tag, successful CI run,
Central publication record, SBOM, checksums, GPG signatures, source POMs, and provenance attestation.
The workflow uploads Central deployment details before collecting the remaining release evidence, so
the deployment status remains available if later evidence collection fails. The final release-evidence
archive contains the deployment log, source commit, GitHub workflow URL, and a checksum manifest; it
is attached to the GitHub Release after Maven Central availability is verified. The tag must identify
the exact non-SNAPSHOT source that generated the published POMs and artifacts.

## License Decisions

Apache-2.0, MIT, and BSD-family licenses are normally acceptable. The automated Dependency Review
gate rejects only the prohibited licenses listed above. A dependency with another license,
dual-license terms, or an unknown license requires a recorded legal and maintainer decision before
introduction. This policy applies to direct and transitive runtime dependencies; test-only
dependencies remain visible in development review but do not appear in a release SBOM.
