# Mason YAML Proof of Concept

## Decision

The four-project Mason YAML proof of concept is technically successful, but a
repository-wide conversion is deferred.

JFoundry can use `pom.yaml` as the sole committed build descriptor for the root,
`jfoundry-domain`, `jfoundry-spring-boot-starter`, and
`jfoundry-helidon-dependencies` while preserving the Maven model, Maven 4 builds,
Maven 3 consumer compatibility, release metadata, and a deterministic Maven 3
publication path. The remaining 118 project descriptors should stay XML until
Maven 4 Central publication is officially supported and IDE import behavior has
been validated for the development team.

This is a build-source experiment. It does not change public Java APIs, runtime
behavior, resolved dependency versions, or the production Central publication
workflow.

## Tested Scope

| Project | Coverage |
| --- | --- |
| Root `jfoundry-parent` | Mixed 122-project reactor, module discovery, plugin management, release profiles, project metadata, and Maven inheritance attributes |
| `jfoundry-domain` | A normal JAR with an inherited parent, managed dependencies, and tests |
| `jfoundry-spring-boot-starter` | A nested starter, Enforcer execution, property expressions, and attached Javadocs |
| `jfoundry-helidon-dependencies` | An independent published BOM with imported BOMs, release metadata, SCM inheritance attributes, signing, and Central configuration |

The converted projects commit only `pom.yaml`. No XML shadow POM is retained.
Mason is registered as `eu.maveniverse.maven.mason:mason:0.3.0` in
`.mvn/extensions.xml`.

## Environment

Evidence was collected on 2026-08-28 with:

- GraalVM Community `25.0.4`;
- Apache Maven `4.0.0-rc-6` through the Maven Wrapper;
- Apache Maven `3.9.16` for the publication bridge and consumer checks;
- Maveniverse Mason `0.3.0`;
- Sonatype Central Publishing Maven Plugin `0.11.0`.

## Results

### Mixed Maven 4 Reactor

`./mvnw -B -DskipTests validate` recognized all 122 projects. Focused tests for
`jfoundry-domain` and `jfoundry-spring-boot-starter` passed, including the
starter's banned-dependency Enforcer rule.

Mason does not reinterpret an explicit XML parent path. Eight unconverted Core
and Runtime aggregators therefore point directly to the root `pom.yaml`. The
disposable Maven 3 tree reverses those paths and the generated
`jfoundry-domain` parent path to `pom.xml`.

Maven model attributes whose XML spelling starts with `@` are represented by
their Maven field names in YAML. For example:

```yaml
child.project.url.inherit.append.path: false
scm:
  child.scm.connection.inherit.append.path: false
  child.scm.developerConnection.inherit.append.path: false
  child.scm.url.inherit.append.path: false
```

### Model Equivalence

`scripts/verify-mason-model-equivalence.sh origin/main WORKTREE` generated and
canonically compared effective models for all four converted projects. The
root, domain, starter, and Helidon BOM models were equivalent to the XML
baseline.

The comparison normalizes the domain parent filename and excludes only the
dedicated `mason-central-poc` profile. Mutations to coordinates, dependencies,
plugin goals, other profiles, and SCM inheritance attributes remain failures.

### Maven 3 Publication Bridge

`scripts/generate-maven3-publication-tree.sh` uses Mason's raw model parser and
Maven 4's model writer to create XML descriptors in a disposable tree. The tree
contains no `pom.yaml`, Mason extension registration, source-workspace absolute
paths, or committed generated files.

The full 122-project tree passed Maven `3.9.16` validation and a local
file-repository `deploy`. This bridge is sufficient to keep the protected
production release workflow on Maven 3.9.16 while YAML remains the committed
source.

### Consumer POM Compatibility

A clean Maven 4 installation into a new temporary local repository completed
for all 122 projects. `scripts/verify-consumer-pom.sh` then verified the
installed parent, flattened modules, starters, and BOMs. Maven 3.9.16 and Maven
4.0.0-rc-6 both compiled the Boot BOM, Cloud BOM, and Spring parent consumer
projects successfully.

Release metadata, release workflow security checks, and aggregate SBOM
configuration also pass with the mixed XML/YAML source layout.

### Maven 4 Central Experiment

The `mason-central-poc` profile implements the workaround tracked by
[MNG-8584](https://github.com/apache/maven/issues/10647):

- standard Maven deploy is skipped;
- the Central plugin is not enabled as a Maven extension;
- `central-publishing:publish` is explicitly bound to `deploy`;
- signing uses a temporary GPG key;
- the Central base URL must be an explicit IPv4 loopback URL;
- real `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` environment variables are
  rejected.

The complete 122-project Maven 4 build succeeded. The local capture server
received one authenticated multipart POST to `/api/v1/publisher/upload` with
11,711,796 request bytes. No Sonatype endpoint was contacted. The generated
bundle contained representative parent, JAR, starter, and BOM POMs; binary,
sources, and Javadoc JARs; GPG signatures; and SHA-256 checksums.

This proves bundle construction and the explicit goal binding. It does not
prove that Sonatype's production service accepts the bundle or that plugin
`0.11.0` is supported on a future Maven 4 final release.

## Remaining Risks

- Apache Maven still tracks Central readiness in
  [MNG-8584](https://github.com/apache/maven/issues/10647). Maven 4 and the
  Sonatype Central Publishing Plugin are released independently.
- The no-upload experiment uses a protocol-compatible loopback capture server,
  not a Sonatype staging or test tenant.
- Production publication still requires the disposable Maven 3.9.16 bridge.
- IDE import, navigation, refactoring, and Maven tool-window behavior for
  `pom.yaml` have not been validated.
- Mason `0.3.0` is an additional core build extension and a new release-time
  dependency. A full conversion would enlarge the migration and maintenance
  surface without improving produced artifacts.

## Adoption Boundary

Keep the production `.github/workflows/release.yml` on Maven 3.9.16. Do not
convert the remaining POMs or remove the Maven 3 bridge until all of the
following are true:

1. Maven 4 Central publication is supported by Apache Maven and Sonatype for a
   final Maven 4 release, or JFoundry validates the explicit publish path
   against a Sonatype-provided safe test facility.
2. IntelliJ IDEA and any other required developer tooling can import and edit
   the mixed or fully converted reactor acceptably.
3. The model-equivalence, Consumer POM, release metadata, SBOM, and publication
   bridge checks remain mandatory gates.
4. The maintenance benefit justifies converting and reviewing the remaining
   118 descriptors.

Until then, this branch is evidence that Mason YAML is viable for JFoundry, not
a decision to make YAML the repository-wide build format.
