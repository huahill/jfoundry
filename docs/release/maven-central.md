# Maven Central Release

This project has Maven Central publishing infrastructure for the public `xfoundries/jfoundry` repository.

## Metadata

The root POM publishes URL and SCM metadata for `https://github.com/xfoundries/jfoundry`. Published Maven coordinates use the verified Central namespace `io.github.xfoundries`, while Java API packages remain under `org.jfoundry.*`.

## Prerequisites

- Java 25.
- The checked-in Maven Wrapper, currently Maven `4.0.0-rc-6` with Consumer POM transformation enabled.
- Maven 4 final for Maven Central publication and the release Consumer POM check. Maven 3 is not a supported JFoundry source-build or release runtime.
- Set repository variable `MAVEN_CENTRAL_MAVEN4_READY` to `true` only after Maven 4 final and Central
  publication have been validated. The release workflow rejects RC/beta/alpha wrappers and fails
  when this variable is absent.
- A Sonatype Central Portal account with publishing rights for `io.github.xfoundries`.
- SNAPSHOT publishing enabled for the `io.github.xfoundries` namespace if publishing development snapshots.
- For local release dry-runs, a Maven server entry named `jfoundry` in `~/.m2/settings.xml`.
- For GitHub Actions publishing, use the repository environment `jfoundry` with these secrets:
  - `CENTRAL_USERNAME`: Sonatype Central Portal username or publishing token username.
  - `CENTRAL_PASSWORD`: Sonatype Central Portal password or publishing token password.
- For GitHub Actions releases only, also provide:
  - `GPG_PRIVATE_KEY`: ASCII-armored private key used to sign artifacts.
  - `GPG_PASSPHRASE`: passphrase for the private key.
- A GPG key available to Maven for local artifact signing.
- Release versions in all reactor POMs. Central releases must not publish `*-SNAPSHOT` versions.
- Real project URL and SCM metadata in `pom.xml`.

Do not commit Maven Central usernames, tokens, passwords, or GPG private keys. Keep them in
local environment variables, local `~/.m2/settings.xml`, or GitHub Actions secrets.

Example server configuration:

```xml
<servers>
  <server>
    <id>jfoundry</id>
    <username>${env.CENTRAL_USERNAME}</username>
    <password>${env.CENTRAL_PASSWORD}</password>
  </server>
</servers>
```

## GitHub Release Environment

Configure the credentials below in the repository environment named `jfoundry`. SNAPSHOT publishing
needs only the Central credentials; releases also need GPG.

| Secret | Value |
|--------|-------|
| `CENTRAL_USERNAME` | Sonatype Central Portal username or publishing token username |
| `CENTRAL_PASSWORD` | Sonatype Central Portal password or publishing token password |
| `GPG_PRIVATE_KEY` (release only) | ASCII-armored private key used to sign artifacts |
| `GPG_PASSPHRASE` (release only) | Passphrase for `GPG_PRIVATE_KEY` |

GitHub path: repository `Settings` -> `Environments` -> `jfoundry` -> `Environment secrets`.
Require maintainer review for this environment before publishing a release.

## Local Verification

Run the regular package build first:

```bash
./mvnw -DskipTests package
```

Then run the release profile through `verify` so sources, Javadocs, and local signatures are exercised:

```bash
./mvnw -Prelease -DskipTests verify
```

The `verify` phase checks local artifact generation and signatures up to the GPG signing step. It does not upload or stage a Central Portal deployment bundle. The protected workflow uses Maven 4 for the complete build, Consumer POM checks, and Central `deploy` lifecycle after the readiness guard passes.

If GPG is not configured locally, the release-profile verification may fail at the signing step. Failures before signing, including compilation, source JARs, Javadocs, metadata, placeholder metadata guards, or Central publishing plugin setup, must be fixed before release.

## Publish

Release publication is performed by the protected GitHub Actions environment, not from an arbitrary
developer checkout. The release tag must point to a committed non-SNAPSHOT reactor whose POM version
matches the tag exactly. For example, `v1.0.0-RC1` must point to source whose root and reactor version
is `1.0.0-RC1`.

The release workflow checks out the requested annotated tag, verifies the tag-to-version relationship,
a clean source tree, and matching SCM tags on every independent publication POM. It then runs
`./mvnw -B -Prelease -DskipTests verify`, installs the complete reactor into an isolated repository,
and verifies Maven 4 Consumer POMs and consumer resolution for both direct Spring BOM imports and
business projects that inherit the supported Spring Boot parent. It checks for open High or Critical
Dependabot alerts, verifies Maven 4 final/Central readiness, and runs the Maven 4 `deploy` lifecycle.
The workflow requires the plugin to report a Central
`deploymentId` before it treats the deployment as successful. It never changes POM versions or
pushes a branch during publication.

Maven 4 is still an RC release. JFoundry uses its official Consumer POM transformation because Maven
Central validates the POM that is deployed, rather than the build-time inheritance model. The source
POMs remain maintainable parent/BOM-based POMs; Maven 4 produces flattened Consumer POMs for child
artifacts. The workflow archives those transformed POMs and checks them with Maven 4 before publication.

The ordinary CI Maven 4 compatibility job performs the same isolated reactor installation and
Maven 4 consumer resolution before a release tag can be created. Its expected Spring Boot
parent version is read from the generated `jfoundry-spring-boot-dependencies` Consumer POM, so the
verification cannot remain green by sharing a stale hardcoded version with its test fixture. Release
POM metadata verification also requires each non-SNAPSHOT independent BOM or parent SCM tag to match
the project version; the next minor SNAPSHOT line retains the immediately preceding stable tag.

Central publication is currently blocked because the wrapper is Maven 4 RC6 and Central readiness has
not been validated. Once Maven 4 final is available, the wrapper executes the standard `deploy`
lifecycle directly. No Maven 3 bridge is retained.

Every JFoundry publication POM, including the independent BOM POMs, configures the Central publishing
plugin with `autoPublish=true` and `waitUntil=PUBLISHED`. The workflow then verifies that Maven
Central's content repository resolves the exact `io.github.xfoundries:jfoundry-parent` POM for the
release version. It never creates the GitHub Release before that consumer-visible availability check
passes.

If Maven Central already exposes that exact coordinate, the workflow does not redeploy the version.
It records the existing publication, regenerates the evidence, and can complete a previously
interrupted GitHub Release publication. Maven Central release versions remain immutable.

Maven versions with a prerelease qualifier, such as `1.0.0-RC1`, produce a GitHub prerelease and
are explicitly excluded from GitHub's Latest release selection. A release without such a qualifier
is published as the normal stable GitHub Release.

## One-Time Spring Boot Parent 1.0.0 Remediation

`.github/workflows/publish-spring-boot-parent-1.0.0.yml` is a temporary, manual-only remediation
for the previously absent `io.github.xfoundries:jfoundry-spring-boot-parent:1.0.0` POM. It may run
only from `main`, requires the exact `PUBLISH_JFOUNDRY_SPRING_BOOT_PARENT_1_0_0` confirmation value,
and uses the protected `jfoundry` environment. The workflow requires that the Parent POM returns
`404` from Maven Central while the already published `jfoundry-dependencies:1.0.0` and the historical
`jfoundry-spring-dependencies:1.0.0` POMs both return `200`. The latter coordinate belongs only to
the 1.0.0 remediation precondition; current releases use the separate
`jfoundry-spring-boot-dependencies` and `jfoundry-spring-cloud-dependencies` lines.

It signs and deploys only `jfoundry-boms/jfoundry-spring-boot-parent/pom.xml`, waits for the new POM
to become visible, and uploads an attested evidence archive. After publication succeeds, it force
updates `v1.0.0` to the reviewed `main` commit, deletes the existing `v1.0.0` GitHub Release, and
creates a new release for that tag. It must never redeploy an existing `1.0.0` coordinate. After
Central publication is confirmed and the workflow evidence is retained, remove this workflow and
`scripts/verify-spring-boot-parent-remediation-workflow.sh` in a follow-up change. Future framework
changes must use a new version line.

## Publish SNAPSHOTs

Central Portal supports publishing `*-SNAPSHOT` versions when SNAPSHOT publishing is enabled for
the namespace. SNAPSHOTs are deployed through Maven's standard deploy flow to the Central Portal
snapshots repository. This avoids the release-only staging path and works for all reactor modules,
including standalone BOM modules.

Publish the current development version locally with:

```bash
./mvnw -DskipTests deploy \
  -DaltDeploymentRepository=jfoundry::https://central.sonatype.com/repository/maven-snapshots/
```

The current reactor version must end with `-SNAPSHOT`. Do not use the release workflow for
SNAPSHOTs; it intentionally rejects SNAPSHOT versions.

GitHub Actions publishes SNAPSHOTs through `.github/workflows/snapshot.yml`. It runs when `main`
is pushed and can also be started manually from the Actions tab. The workflow verifies that the
current root version is a SNAPSHOT before deploying:

```bash
./mvnw -B -DskipTests deploy \
  -DaltDeploymentRepository=jfoundry::https://central.sonatype.com/repository/maven-snapshots/
```

Consumers must add the Central Portal snapshots repository to resolve these versions:

```xml
<repositories>
  <repository>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

## GitHub Release Publishing

1. Merge the committed release-version change after the complete CI matrix passes.
2. Create and push an annotated tag such as `v1.0.0-RC1` for that exact commit.
3. From the `main` branch, manually run the `Release` workflow with `release_tag=v1.0.0-RC1` and
   approve the `jfoundry` environment when its reviewers have inspected the candidate. The workflow
   rejects runs started from a tag or any other branch so that it always uses the current reviewed
   release workflow definition while checking out the immutable release tag as its source.
4. Wait for the workflow to publish Central, verify public availability, upload and attest release
   evidence, and create the GitHub Release. The Release display title matches the immutable tag exactly,
   including its leading `v`; rerunning the workflow also restores that title if it was edited manually.
5. After a successful stable release, `Prepare next SNAPSHOT` creates a short-lived branch and pull
   request for the next minor `-SNAPSHOT` development version. Review and merge that PR before the
   next development changes. The workflow skips prerelease tags and does not write directly to `main`.

Retry an interrupted workflow only when its immutable source remains correct. If the release source
must change, choose a new version and repeat the immutable-tag process; never move an existing tag.
