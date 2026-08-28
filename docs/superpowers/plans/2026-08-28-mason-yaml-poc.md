# Mason YAML Proof-of-Concept Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove whether four representative JFoundry projects can use Mason YAML as their sole committed build source while retaining Maven 4 builds, Maven 3 consumer and publication compatibility, release metadata, and a no-upload Central publishing path.

**Architecture:** Register Mason once as a Maven 4 core extension, keep the reactor mixed, and verify semantic equivalence against the XML source at `origin/main`. Shell test drivers enforce source invariants and exercise disposable XML generation for Maven 3; a separate profile exercises the Maven 4 Central plugin workaround using local/deferred output only.

**Tech Stack:** Java 25, Apache Maven 4.0.0-rc-6, Apache Maven 3.9.16, Maveniverse Mason 0.3.0, Bash, structured XML processing.

**Spec:** `docs/superpowers/specs/2026-08-28-mason-yaml-poc-design.md`

## Global Constraints

- The source of truth for converted projects is `pom.yaml`; no committed XML shadow POM is allowed.
- Convert only the root, `jfoundry-domain`, `jfoundry-spring-boot-starter`, and `jfoundry-helidon-dependencies`.
- Keep model version `4.0.0` and Mason version `0.3.0` unless a failing test proves the released dialect cannot represent the source model.
- Keep `.github/workflows/release.yml` on Maven 3.9.16 and do not contact Sonatype.
- Generated XML POMs, repositories, settings, bundles, and logs must remain under validated temporary directories.
- All source comments, scripts, commit messages, and technical documentation are English.
- Run Java 25 release compatibility verification before proposing broader adoption.

---

### Task 1: PoC Source Contract And Mixed-Reactor Invariants

**Files:**
- Create: `.mvn/extensions.xml`
- Create: `scripts/verify-mason-poc-test.sh`
- Create: `scripts/verify-mason-poc.sh`
- Modify: `docs/superpowers/specs/2026-08-28-mason-yaml-poc-design.md`

**Interfaces:**
- Consumes: Maven Wrapper `4.0.0-rc-6`, the four selected project paths, and the eight XML aggregators whose parent resolves directly to the root.
- Produces: `scripts/verify-mason-poc.sh`, a zero-argument repository contract check used by every later task.

- [ ] **Step 1: Write the failing shell self-test**

  The test copies the verifier to a temporary fixture, creates invalid extension/source layouts, and asserts that it rejects a missing Mason extension, a retained XML shadow POM, an incorrect Mason version, and an XML child that still points at the root `pom.xml`.

- [ ] **Step 2: Run the self-test and verify RED**

  Run: `bash scripts/verify-mason-poc-test.sh`

  Expected: failure because `scripts/verify-mason-poc.sh` does not exist.

- [ ] **Step 3: Implement the source verifier and Mason registration**

  Add `fr.jcgay.maven:maven-mason:0.3.0` to `.mvn/extensions.xml`. The verifier checks exact extension coordinates, the four `pom.yaml` files with absent sibling `pom.xml` files, the eight explicit root `pom.yaml` parent paths, the unchanged Maven Wrapper RC6 version, and the unchanged Maven 3.9.16 production release workflow.

- [ ] **Step 4: Run the self-test and repository verifier**

  Run: `bash scripts/verify-mason-poc-test.sh && bash scripts/verify-mason-poc.sh`

  Expected: the self-test passes; the repository verifier remains RED until Task 2 converts the descriptors.

- [ ] **Step 5: Commit the contract separately from conversion**

  Commit message: `test(build): define Mason YAML proof-of-concept contract`

### Task 2: Mason YAML Conversion And Parent Resolution

**Files:**
- Create: `pom.yaml`
- Create: `jfoundry-core/jfoundry-domain/pom.yaml`
- Create: `jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.yaml`
- Create: `jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml`
- Delete: the corresponding four `pom.xml` files
- Modify: eight unconverted top-level Core and Runtime aggregator POMs that resolve the root parent directly

**Interfaces:**
- Consumes: Mason 0.3.0 YAML field mapping, including quoted `@...` keys for model attributes.
- Produces: a mixed Maven 4 reactor with YAML as the only committed source for four projects.

- [ ] **Step 1: Confirm the repository contract fails for missing YAML sources**

  Run: `bash scripts/verify-mason-poc.sh`

  Expected: failure naming the first missing `pom.yaml`.

- [ ] **Step 2: Translate the four XML models without semantic changes**

  Preserve coordinates, properties, modules, dependency management, dependencies, plugins, executions, nested configuration, profiles, metadata, and the root and Helidon SCM inheritance attributes. Use expanded dependency coordinates where compact syntax is ambiguous.

- [ ] **Step 3: Redirect the eight unconverted top-level XML aggregators**

  Replace only their root parent `<relativePath>` target from `pom.xml` to `pom.yaml`. The converted `jfoundry-domain` YAML parent points at `../../pom.yaml`; the converted starter continues to point at its XML Spring parent.

- [ ] **Step 4: Run source, parse, reactor, and focused module checks**

  Run: `bash scripts/verify-mason-poc.sh`

  Run: `./mvnw -B -DskipTests validate`

  Run: `./mvnw -B -pl jfoundry-core/jfoundry-domain test`

  Run: `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter -am test`

  Expected: all commands exit zero; the starter Enforcer execution remains active.

- [ ] **Step 5: Commit the descriptor conversion**

  Commit message: `build: convert representative POMs to Mason YAML`

### Task 3: Normalized Maven Model Equivalence

**Files:**
- Create: `scripts/verify-mason-model-equivalence-test.sh`
- Create: `scripts/verify-mason-model-equivalence.sh`

**Interfaces:**
- Consumes: an optional baseline Git ref (default `origin/main`), the current YAML candidate, Maven 4, and temporary directories.
- Produces: normalized effective-model snapshots for the four converted projects and a semantic diff exit status.

- [ ] **Step 1: Write mutation-based failing tests**

  Create a temporary minimal baseline/candidate pair and prove that the verifier rejects changed coordinates, dependencies, plugin goals/configuration, profiles, project metadata, and SCM attributes while ignoring temporary absolute build paths and unordered model collections.

- [ ] **Step 2: Run the self-test and verify RED**

  Run: `bash scripts/verify-mason-model-equivalence-test.sh`

  Expected: failure because the model verifier is absent.

- [ ] **Step 3: Implement isolated model extraction and normalization**

  Materialize `origin/main` with `git archive` into a temporary baseline, run Maven 4 `help:effective-pom` for each selected project in baseline and candidate copies, parse XML with a structured tool, remove Maven-generated absolute directories and source-location noise, sort unordered collections by stable keys, and compare normalized documents. Always clean temporary directories while preserving the primary failure.

- [ ] **Step 4: Run the self-test and real model comparison**

  Run: `bash scripts/verify-mason-model-equivalence-test.sh`

  Run: `bash scripts/verify-mason-model-equivalence.sh origin/main`

  Expected: both exit zero with no unexplained semantic diff.

- [ ] **Step 5: Commit model verification**

  Commit message: `test(build): verify Mason model equivalence`

### Task 4: Disposable Maven 3 Publication Bridge

**Files:**
- Create: `scripts/generate-maven3-publication-tree.sh`
- Create: `scripts/verify-mason-maven3-bridge-test.sh`
- Create: `scripts/verify-mason-maven3-bridge.sh`

**Interfaces:**
- Consumes: repository root, destination temporary directory, Maven 4 Wrapper, and a Maven 3.9.16 executable.
- Produces: a disposable source tree with XML descriptors at the four converted paths and the eight top-level parent paths reversed to `pom.xml`.

- [ ] **Step 1: Write bridge safety and fidelity tests**

  Assert rejection of a destination inside the repository, refusal to overwrite a non-empty destination, absence of `pom.yaml` in the result, absence of workspace absolute paths, correct parent-path reversal, successful Maven 3 `validate`, and successful local file-repository deploy without network publication.

- [ ] **Step 2: Run the bridge test and verify RED**

  Run: `bash scripts/verify-mason-maven3-bridge-test.sh`

  Expected: failure because the generator does not exist.

- [ ] **Step 3: Implement deterministic XML generation**

  Copy tracked source into a validated empty temporary destination. Generate build POMs from the Maven 4 parsed raw model rather than committing shadows; strip machine paths and Maven-injected defaults not present in the source. Replace the eight root parent targets with `pom.xml`, remove YAML descriptors and Mason extension registration from the disposable tree, and keep `.mvn/maven.config` Consumer POM settings.

- [ ] **Step 4: Verify with Maven 3.9.16**

  Run: `bash scripts/verify-mason-maven3-bridge-test.sh`

  Run: `bash scripts/verify-mason-maven3-bridge.sh "$(command -v mvn)"`

  Expected: Maven 3 `validate` and local file-repository `deploy` exit zero and never read YAML.

- [ ] **Step 5: Commit the Maven 3 bridge**

  Commit message: `build: add disposable Maven 3 publication bridge`

### Task 5: No-Upload Maven 4 Central Publishing Experiment

**Files:**
- Modify: `pom.yaml`
- Modify: `jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml`
- Create: `scripts/verify-mason-central-poc-test.sh`
- Create: `scripts/verify-mason-central-poc.sh`

**Interfaces:**
- Consumes: a `mason-central-poc` profile, Central Publishing Plugin 0.11.0 local/deferred parameters, loopback endpoint validation, and fake settings credentials.
- Produces: a local Central bundle or a reproducible, explicitly classified plugin limitation without real upload.

- [ ] **Step 1: Write no-upload guard tests**

  Assert that the experiment rejects non-loopback `centralBaseUrl`, forbids real credential environment variables, requires `maven.deploy.skip=true`, requires `extensions` to be absent/false in the PoC execution, and requires an explicit `publish` goal bound to `deploy`.

- [ ] **Step 2: Run the guard test and verify RED**

  Run: `bash scripts/verify-mason-central-poc-test.sh`

  Expected: failure because the profile and verifier do not exist.

- [ ] **Step 3: Add the isolated PoC profile and runner**

  Add a profile that follows MNG-8584 without changing the existing `release` profile: skip standard deploy, bind `central-publishing:publish` explicitly, direct staging/output to a validated temporary directory, and use only loopback/fake settings. The runner starts a loopback capture server only if deferred mode cannot produce a complete bundle.

- [ ] **Step 4: Execute and inspect the local result**

  Run: `bash scripts/verify-mason-central-poc-test.sh`

  Run: `bash scripts/verify-mason-central-poc.sh`

  Expected: no request reaches a non-loopback host; the output bundle contains representative POM, JAR, sources, Javadocs, signatures, and checksums, or the script exits with a documented upstream-limitation classification and retained diagnostic log path.

- [ ] **Step 5: Commit the Central experiment**

  Commit message: `test(release): add Maven 4 Central publishing PoC`

### Task 6: Release Contracts, Consumer POMs, And Adoption Decision

**Files:**
- Modify: `docs/release/compatibility.md`
- Create: `docs/release/mason-yaml-poc.md`
- Modify only if required by verified behavior: existing release metadata, release workflow, and Consumer POM verifiers

**Interfaces:**
- Consumes: all PoC verifier results, existing release/Consumer POM scripts, Java 25, Maven 4 RC6, and Maven 3.9.16.
- Produces: reproducible evidence and an adopt/defer recommendation with explicit remaining upstream risks.

- [ ] **Step 1: Run existing release-script self-tests before adaptations**

  Run: `bash scripts/verify-release-pom-metadata-test.sh`

  Run: `bash scripts/verify-release-workflow-test.sh`

  Run: `bash scripts/verify-consumer-pom-test.sh`

  Expected: any failure specifically identifies an XML-source assumption; preserve all metadata and workflow security assertions when adapting it to YAML-aware structured reads.

- [ ] **Step 2: Run Consumer POM and bridge verification on a clean local repository**

  Run: `./mvnw -B -DskipTests clean install`

  Run: `bash scripts/verify-consumer-pom.sh <temporary-local-repository> 1.4.0-SNAPSHOT "<maven-3.9.16>" "$(pwd)/mvnw"`

  Run: `bash scripts/verify-release-pom-metadata.sh`

  Expected: representative parent, JAR, starter, and BOM Consumer POMs are accepted by Maven 3.9.16 and Maven 4 RC6 with unchanged release metadata.

- [ ] **Step 3: Run all PoC and repository-wide compatibility gates**

  Run all four PoC self-tests and verifiers, then run `scripts/verify-ci-matrix.sh`.

  Expected: all mandatory gates pass on Java 25; the Central experiment may report only the explicitly documented upstream limitation allowed by the design.

- [ ] **Step 4: Record exact evidence and recommendation**

  Document tool versions, commands, semantic differences, IDE observation if available, Maven 3 bridge behavior, Central bundle result, unresolved Mason issues, and an adopt/defer conclusion in both the PoC report and compatibility matrix. Do not generalize beyond the four-project sample.

- [ ] **Step 5: Commit evidence and documentation**

  Commit message: `docs(build): record Mason YAML proof-of-concept results`
