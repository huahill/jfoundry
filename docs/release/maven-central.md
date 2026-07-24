# Maven Central Release

This project has Maven Central publishing infrastructure for the public `xfoundries/jfoundry` repository.

## Metadata

The root POM publishes URL and SCM metadata for `https://github.com/xfoundries/jfoundry`. Published Maven coordinates use the verified Central namespace `io.github.xfoundries`, while Java API packages remain under `org.jfoundry.*`.

## Prerequisites

- Java 25.
- Maven 3.9.0 or newer.
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

## GitHub Secrets To Add Later

When the Central Portal namespace is ready, add the credentials below to the repository environment
named `jfoundry`. SNAPSHOT publishing needs only the Central credentials; releases also need GPG.

| Secret | Value |
|--------|-------|
| `CENTRAL_USERNAME` | Sonatype Central Portal username or publishing token username |
| `CENTRAL_PASSWORD` | Sonatype Central Portal password or publishing token password |
| `GPG_PRIVATE_KEY` (release only) | ASCII-armored private key used to sign artifacts |
| `GPG_PASSPHRASE` (release only) | Passphrase for `GPG_PRIVATE_KEY` |

GitHub path: repository `Settings` -> `Environments` -> `jfoundry` -> `Environment secrets`.
Add protection reviewers to the environment if release publication should require manual approval.

## Local Verification

Run the regular package build first:

```bash
mvn -DskipTests package
```

Then run the release profile through `verify` so sources, Javadocs, and local signatures are exercised:

```bash
mvn -Prelease -DskipTests verify
```

The `verify` phase checks local artifact generation and signatures up to the GPG signing step. It does not upload or stage a Central Portal deployment bundle; that behavior is triggered during `deploy`.

If GPG is not configured locally, the release-profile verification may fail at the signing step. Failures before signing, including compilation, source JARs, Javadocs, metadata, placeholder metadata guards, or Central publishing plugin setup, must be fixed before release.

## Publish

After replacing placeholder metadata, setting release versions, and configuring Central credentials and GPG, deploy locally with the release profile:

```bash
mvn -Prelease -DskipTests deploy
```

The Central publishing plugin is configured with `autoPublish=false`, so the deployment uploads a staged deployment to Central Portal for manual review and publishing.

## Publish SNAPSHOTs

Central Portal supports publishing `*-SNAPSHOT` versions when SNAPSHOT publishing is enabled for
the namespace. SNAPSHOTs are deployed through Maven's standard deploy flow to the Central Portal
snapshots repository. This avoids the release-only staging path and works for all reactor modules,
including standalone BOM modules.

Publish the current development version locally with:

```bash
mvn -DskipTests deploy \
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

The `Release` workflow publishes automatically when a GitHub Release is published.

Use a tag such as `v1.0.0`. The workflow strips the leading `v`, sets all reactor POM versions
to `1.0.0`, runs `./mvnw -B -Prelease -DskipTests deploy`, then updates the default branch to
the next development version in a separate job and Git worktree. The release commit itself is not
pushed back to the default branch; the tag/GitHub Release identifies the immutable release version.

The publish and default-branch bump steps are intentionally separate jobs. If the Central staged
deployment succeeds but the default-branch push is rejected by branch protection or a non-fast-forward
race, rerun only the failed bump job; do not rerun the publish job for the same release version.

By default, the next development version is inferred as the next patch version, for example `1.0.0` becomes `1.0.1-SNAPSHOT`. To override it, add a line to the GitHub Release notes:

```text
Next-Snapshot: 1.1.0-SNAPSHOT
```

Manual workflow runs require `release_version` and can optionally set `next_snapshot_version`.

The publish job uses the Central publishing plugin with `autoPublish=false`, so a successful
GitHub Release upload creates a staged Central Portal deployment for manual review and publishing.
