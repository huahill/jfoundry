# Maven Central Release

This project has Maven Central publishing infrastructure for the public `xfoundries/jfoundry` repository.

## Metadata

The root POM publishes URL and SCM metadata for `https://github.com/xfoundries/jfoundry`. Published Maven coordinates use the verified Central namespace `io.github.xfoundries`, while Java API packages remain under `org.jfoundry.*`.

## Prerequisites

- Java 25.
- The checked-in Maven Wrapper, currently Maven `4.0.0-rc-5` with Consumer POM transformation enabled.
- Maven 3.9.x for the release consumer-compatibility check.
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

The `verify` phase checks local artifact generation and signatures up to the GPG signing step. It does not upload or stage a Central Portal deployment bundle; that behavior is triggered during `deploy`.

If GPG is not configured locally, the release-profile verification may fail at the signing step. Failures before signing, including compilation, source JARs, Javadocs, metadata, placeholder metadata guards, or Central publishing plugin setup, must be fixed before release.

## Publish

Release publication is performed by the protected GitHub Actions environment, not from an arbitrary
developer checkout. The release tag must point to a committed non-SNAPSHOT reactor whose POM version
matches the tag exactly. For example, `v1.0.0-RC1` must point to source whose root and reactor version
is `1.0.0-RC1`.

The release workflow checks out the requested annotated tag, verifies the tag-to-version relationship
and a clean source tree, runs `./mvnw -B -Prelease -DskipTests verify`, installs the complete reactor
into an isolated repository, verifies the Maven 4 Consumer POMs and Maven 3.9/Maven 4 consumer
resolution, checks for open High or Critical Dependabot alerts, and only then runs `deploy`. It
never changes POM versions or pushes a branch during publication.

Maven 4 is still an RC release. JFoundry uses its official Consumer POM transformation because Maven
Central validates the POM that is deployed, rather than the build-time inheritance model. The source
POMs remain maintainable parent/BOM-based POMs; Maven 4 publishes flattened Consumer POMs for child
artifacts. The workflow archives those transformed POMs in the release evidence and checks that Maven
3.9 and Maven 4 can consume them before deployment.

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
3. Manually run the `Release` workflow with `release_tag=v1.0.0-RC1` and approve the `jfoundry`
   environment when its reviewers have inspected the candidate.
4. Wait for the workflow to publish Central, verify public availability, upload and attest release
   evidence, and create the GitHub Release.
5. Merge a separate change that starts the next `-SNAPSHOT` development version.

Retry an interrupted workflow only when its immutable source remains correct. If the release source
must change, choose a new version and repeat the immutable-tag process; never move an existing tag.
