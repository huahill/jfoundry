# Mason YAML Proof of Concept

## Decision

The repository-wide Mason YAML proof of concept is technically successful. All
122 reactor projects use `pom.yaml`, and the only source-tree `pom.xml` is the
intentional Maven 3 consumer fixture under `jfoundry-spring-boot-parent`.

Mason preserves the effective Maven model, Maven 4 lifecycle behavior, Maven 3
consumer compatibility, release metadata, runtime integration behavior, and a
deterministic Maven 3 publication path. The complete Java 25 and runtime matrix
was exercised from the YAML source tree. No generated XML shadow POM is
committed.

This remains an experimental build-source conversion rather than an adoption
decision. Production publication still needs a disposable Maven 3 tree, and
IDE and dependency-update tooling have not received equivalent validation.
This branch is non-mergeable POC evidence unless repository-wide Mason adoption
is explicitly approved after the remaining risks are accepted.

## Source Layout

Mason is registered as `eu.maveniverse.maven.mason:mason:0.3.0` in
`.mvn/extensions.xml`. Every reactor project commits only `pom.yaml`.

The conversion is reproducible through:

- `scripts/support/convert-xml-pom-to-mason-yaml.rb` for one descriptor;
- `scripts/convert-mason-reactor.sh` for the complete reactor;
- `scripts/verify-mason-poc.sh` for source-layout invariants;
- `scripts/verify-mason-model-equivalence.sh` for aggregate effective-model
  comparison.

The source verifier recursively follows every `modules` declaration from the
root, requires exactly 122 reachable YAML projects, and rejects missing or
orphaned modules, XML shadow POMs, XML parent paths in YAML models, unsupported
`@...` YAML keys, missing Mason registration, and any unexpected XML descriptor
outside the explicit consumer fixture.

## Environment

Evidence was collected on 2026-08-28 with:

- GraalVM Community `25.0.4`;
- Apache Maven `4.0.0-rc-6` through the Maven Wrapper;
- Apache Maven `3.9.16` for publication and consumer checks;
- Maveniverse Mason `0.3.0`;
- Sonatype Central Publishing Maven Plugin `0.11.0`;
- Docker Desktop `29.7.2` for the final Native Image checks.

## Model Fidelity

The aggregate effective models for all 122 projects were generated from the
pre-conversion baseline and the YAML source tree, normalized, and compared
canonically. They are equivalent after normalizing YAML/XML parent filenames
and excluding the POC-only `mason-central-poc` profile from both trees.

Maven model warnings were also compared once as recorded POC evidence. Both
trees completed successfully with 1,834 warning lines, 1,659 reported model
problems, and four reactor BOM warnings. After normalizing paths, line locations,
and summaries, the warning multisets are identical. Mason changes source-location
attribution but introduces no semantic model warning. The aggregate model verifier
does not automate this warning-log comparison.

Maven XML attributes whose names start with `@` use their Maven model field
names in YAML. For example:

```yaml
child.project.url.inherit.append.path: false
scm:
  child.scm.connection.inherit.append.path: false
  child.scm.developerConnection.inherit.append.path: false
  child.scm.url.inherit.append.path: false
```

### Mason 0.3.0 Configuration Limitation

Mason maps a plain YAML sequence according to its inferred XML item name. That
inference is incorrect for Maven Compiler's nonstandard
`annotationProcessorPaths/path` shape: a plain sequence becomes
`annotationProcessorPath`, which Maven Compiler does not accept.

The 11 Quarkus deployment projects therefore use the explicit wrapper:

```yaml
annotationProcessorPaths:
  path:
    groupId: io.quarkus
    artifactId: quarkus-extension-processor
```

The converter applies this shape deliberately and its fixture tests protect
the mapping. Other plugin configurations must be reviewed for nonstandard XML
collection item names before conversion.

## Quarkus Model Handoff

Quarkus tests consume the application model already resolved by Maven instead
of reconstructing the workspace in the forked Surefire JVM. The forked JVM
does not contain Maven's core-extension container, so its fallback
`WorkspaceLoader` cannot apply Mason semantics to `pom.yaml`.

Quarkus modules using `@QuarkusTest` bind
`quarkus-maven-plugin:generate-code-tests`. The goal serializes Maven's resolved
application model for the test runtime. `skipSourceGeneration` follows
`skipTests`, preventing package and publication commands from resolving
deployment artifacts before their reactor modules have been built.

Quarkus issue [#56270](https://github.com/quarkusio/quarkus/issues/56270) records
the diagnosis, and documentation pull request
[#56271](https://github.com/quarkusio/quarkus/pull/56271) documents the required
model handoff upstream.

## Maven 3 Publication Bridge

`scripts/generate-maven3-publication-tree.sh` uses Mason's raw model parser and
Maven 4's model writer to create XML descriptors in a disposable tree. The
generated tree contains no `pom.yaml`, Mason extension registration,
source-workspace absolute paths, or committed generated files.

The full 122-project tree passed Maven `3.9.16` validation and a local
file-repository `deploy`. The protected release workflow generates this tree
before starting Maven 3 and collects signed artifact evidence from it. A new
release runs Maven 3 `deploy`; a retry for an already-published immutable version
runs Maven 3 `verify`, so both paths rebuild evidence from the same XML tree. The
historical Spring Boot Parent remediation instead materializes the exact 1.0.0
XML POM from an immutable commit and never converts the current reactor.

This bridge keeps YAML as the committed source while Sonatype Central
publication remains on Maven 3.9.16.

## Release And Consumer Verification

A clean Maven 4 installation into a new temporary local repository completed
for all 122 projects. `scripts/verify-consumer-pom.sh` verified flattened child
POMs, direct Spring BOM lines, the Spring Boot parent, and Cloud Alibaba
versionless resolution. Maven 3.9.16 and Maven 4.0.0-rc-6 both compiled the
consumer projects.

Release metadata, aggregate SBOM checks, release workflow guards, and signed
Central no-upload verification passed. The no-upload server received one
authenticated multipart POST to `/api/v1/publisher/upload`; the signed bundle
and checksums were verified locally, and no Sonatype endpoint was contacted.

This proves bundle construction and explicit Central goal binding. It does not
prove that Sonatype's production service accepts a Maven 4-produced bundle or
that Central plugin `0.11.0` supports a future Maven 4 final release.

## Version Workflow

`versions-maven-plugin:versions:set` does not support the Mason YAML source
model. Version `2.21.0` attempts to parse `pom.yaml` as XML and fails at the
first character.

The post-release workflow therefore uses
`scripts/set-mason-reactor-version.rb`. It parses the YAML syntax tree, updates
only the root/project versions, JFoundry parent versions, and
`jfoundry.version`, and preserves comments, quoting, ordering, and
`project.build.outputTimestamp`. The committed four-POM fixture covers those
rules and rejection of an unclassified occurrence. A disposable full-reactor
run updated 123 version references across all 122 POMs.

## Runtime Matrix

The following checks passed from the complete YAML source tree:

- the full 122-project Java 25 matrix;
- Spring middleware integration tests;
- Quarkus and Helidon PostgreSQL middleware integration tests;
- Spring base, MyBatis-Plus, and Redisson Native Image tests;
- Quarkus Native Image generation and 17 native integration tests;
- Helidon Native Image startup and HTTP probe.

Spring JobRunr Native Image generation completed, but the executable exited
during JobRunr database migration discovery. JobRunr `8.8.2` passes
`resource:/resources` to GraalVM 25.0.4, which rejects it because a Native Image
resource URI must include a root identifier. The same command on the
pre-conversion XML baseline fails with the same exception and stack, so this is
not a Mason regression. It remains a separate JobRunr/GraalVM compatibility
failure.

## Remaining Risks

- Apache Maven tracks Central readiness in
  [MNG-8584](https://github.com/apache/maven/issues/10647). Maven 4 and the
  Sonatype Central Publishing Plugin are released independently.
- The no-upload experiment uses a protocol-compatible loopback capture server,
  not a Sonatype staging or test tenant.
- Production publication still requires the disposable Maven 3.9.16 bridge.
- IntelliJ IDEA import, navigation, refactoring, and Maven tool-window behavior
  for the 122-project YAML tree have not been validated.
- Dependabot's ability to discover and update Mason `pom.yaml` files has not
  been established. The auto-merge scope recognizes YAML-only Maven changes,
  but that does not prove Dependabot can produce those changes.
- Mason `0.3.0` is an additional core build extension and release-time
  dependency. Nonstandard plugin collection mappings require explicit review.

## Adoption Boundary

Keep Central publication on Maven 3.9.16 and retain all model, Consumer POM,
metadata, SBOM, and publication-tree gates. Do not treat this branch as a
production migration until:

1. Maven 4 Central publication is supported by Apache Maven and Sonatype for a
   final Maven 4 release, or the explicit publish path is validated against a
   Sonatype-provided safe test facility.
2. Required IDE workflows are acceptable for the complete YAML reactor.
3. Dependabot or a replacement dependency-update workflow is proven against
   Mason YAML.
4. The additional extension and conversion maintenance cost is accepted.

The full-reactor PoC demonstrates that Mason YAML is viable for JFoundry's
build and release model. It does not by itself authorize production adoption.
