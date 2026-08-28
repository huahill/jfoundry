# Compatibility Matrix

This document defines the supported platform lines, validated capability scope, and known limitations
for the current JFoundry development line. Runtime BOM POMs are the executable source of exact
platform versions. The table below records stable support lines and their owning BOMs;
`scripts/verify-compatibility-matrix.sh` keeps those boundaries aligned with the BOMs without coupling
documentation to patch-level updates.

## Runtime Platform Baselines

| Platform | Supported line | Version source |
|----------|----------------|----------------|
| Spring Boot-only | 4.1.x | `jfoundry-spring-boot-dependencies` |
| Spring Cloud | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Spring Cloud Alibaba | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Quarkus | 3.39.x | `jfoundry-quarkus-dependencies` |
| Helidon MP | 4.5.x | `jfoundry-helidon-dependencies` |

Spring Cloud applications use Spring Boot 4.0.x; the current consumer compatibility check uses Spring
Boot 4.0.7. The Cloud BOM deliberately does not manage Spring Boot, so the application parent remains
the version source for that part of the combination. Boot-only applications use Spring Boot 4.1.x and
must not combine the Boot-only and Cloud runtime BOMs.

## Toolchain Baselines

| Area | Supported baseline |
|------|--------------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Native Image | GraalVM 25 |
| Maven source descriptor model | 4.1.0 (Maven 4-only YAML model through Mason) |
| Maven wrapper and release tool | 4.0.0-rc-6 (Maven 4-only; publication blocked until final) |
| Maven 3 source-build compatibility | Not supported |

## Consumer Composition

The complete 122-project Mason YAML proof of concept was verified on 2026-08-29 with Maven
4.0.0-rc-6, Mason 0.3.0, and Java 25. Quarkus tests consume Maven's resolved model through
`generate-code-tests`, so the normal Java 25 test matrix runs directly from the YAML source tree.
See [Mason YAML Proof of Concept](mason-yaml-poc.md) for the conversion boundary, validation
evidence, and remaining adoption risks.

Every business application imports `jfoundry-dependencies` and adds only the runtime BOM and
capabilities it needs:

- Spring Boot-only applications use `jfoundry-spring-boot-dependencies` or
  `jfoundry-spring-boot-parent`.
- Spring Cloud applications use their own compatible Spring Boot parent and import
  `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies`.
- Quarkus and Helidon applications import their matching runtime BOM before
  `jfoundry-dependencies`.

Runtime BOMs manage platform ecosystem versions only. They do not add runtime capabilities to the
classpath, manage JFoundry module versions, or import Foundation. Applications still select each
starter, adapter, database, and broker explicitly.

## Validated Capability Scope

The Merge gate requires the current commit's applicable JVM, middleware, Native Image, packaging, and
consumer-POM jobs to succeed. The workflow result for that commit is the current evidence; this page
describes the stable scope those jobs cover rather than copying transient PASS or pending states.

| Runtime | JVM and middleware scope | Native Image scope |
|---------|--------------------------|--------------------|
| Spring | Runtime assembly plus PostgreSQL, MySQL, Kafka, RabbitMQ, RocketMQ, Redis/Redisson, MyBatis-Plus, JPA, Outbox, and Inbox integration paths | Base runtime and Web MVC plus MyBatis-Plus/PostgreSQL, Redisson/Redis, and JobRunr/PostgreSQL capability checks |
| Quarkus | CDI, JTA, JPA, REST, messaging, Outbox/Inbox, and PostgreSQL runtime wiring | Quarkus consumer startup and runtime smoke path |
| Helidon MP | CDI, JTA, JPA, REST, Outbox/Inbox, scheduling, Problem Details, and PostgreSQL runtime wiring | CDI/Web startup and Problem Details response only |

<<<<<<< HEAD
This matrix does not certify arbitrary downstream dependency graphs, databases, brokers, deployment
targets, or application configuration. Consumers must run acceptance tests for their selected
capability set and operational environment.

## Managed Compatibility Exceptions
=======
Historic evidence was recorded on 2026-06-27 with local Java `21.0.10-tem` and Maven wrapper `3.9.16`. The current release-baseline evidence was recorded on 2026-07-24 with GraalVM Community `25.0.2` and Maven wrapper `3.9.16`. The Spring MyBatis-Plus, Redisson, and JobRunr Native Image verification evidence was recorded on 2026-07-30 with the same GraalVM and Maven versions. HTTP logging runtime verification was refreshed on 2026-08-25 with GraalVM Community `25.0.4` and Maven wrapper `4.0.0-rc-6`. Maven 4 Consumer POM release verification was added on 2026-08-01 and now runs with RC6; ordinary CI and the release workflow perform a clean Maven 4 install and verify the installed POMs with Maven 3.9 and Maven 4 before deployment. The four-project Mason YAML proof of concept was verified on 2026-08-28 with GraalVM Community `25.0.4`, Maven `4.0.0-rc-6`, Maven `3.9.16`, and Mason `0.3.0`; Quarkus tests consume Maven's resolved model through `generate-code-tests`. See [Mason YAML Proof of Concept](mason-yaml-poc.md).

| Gate | Command | Result |
|------|---------|--------|
| Unit tests | `./mvnw -B clean test` | PASS on Java 25 |
| Package artifacts | `./mvnw -B -DskipTests package` | PASS on Java 25 |
| Spring middleware integration tests | `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Spring Native Image base Starter/Web MVC consumer smoke test | GraalVM 25 with the Boot-only Spring Boot 4.1.1 AOT line, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative package`, then `GET /jfoundry/native/ready` | Requires CI revalidation after the 4.1.1 baseline upgrade |
| Spring Native Image MyBatis-Plus persistence integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, MyBatis-Plus 3.5.17 and its `mybatis-plus-spring-boot-native-image` module, PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-mybatis-plus verify` | Requires CI revalidation after the 4.1.1 baseline upgrade |
| Spring Native Image Redisson lock integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, Redisson 4.7.0, and Redis, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-redisson verify` | Pending Docker-backed verification; the Spring starter uses the Foundation BOM's 4.7.0 constraint |
| Spring Native Image JobRunr Outbox integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, JobRunr 8.8.1, and PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-jobrunr clean verify` | Pending Docker-backed verification; dependency resolution aligns the starter and core library at 8.8.1 |
| Quarkus PostgreSQL middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Helidon PostgreSQL/JTA middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Release guard | `mvn -Prelease -DskipTests validate` | Expected fail fast on `Release builds require non-SNAPSHOT project versions.` |
| Maven 4 validate | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests validate -e` | PASS |
| Maven 4 package | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests package` | PASS on 2026-08-24; Maven 4 reports imported-BOM model warnings |
| Maven Consumer POM contract | Maven `4.0.0-rc-6`, clean `install`, then `scripts/verify-consumer-pom.sh` with Maven 3.9 and Maven 4 RC6 | PASS on 2026-08-24; verifies flattened child POMs, both direct Spring BOM lines, the Boot parent, and Cloud Alibaba versionless resolution with Maven 3.9 and Maven 4 |
| Mason YAML mixed reactor | `scripts/verify-mason-poc.sh`, focused tests, `scripts/verify-mason-model-equivalence.sh "$(git merge-base origin/main HEAD)"`, and `scripts/verify-ci-matrix.sh` | PASS on 2026-08-28 for the source contract, equivalent models, and the complete 122-project Java 25 matrix; Quarkus modules using `@QuarkusTest` bind `generate-code-tests` and skip it with `-DskipTests` |
| Mason Maven 3 publication bridge | `scripts/verify-mason-maven3-bridge.sh "$(command -v mvn)"` | PASS on 2026-08-28 for Maven 3.9.16 validation and local file-repository deploy across 122 projects |
| Maven 4 Central no-upload PoC | `scripts/verify-mason-central-poc.sh` | PASS on 2026-08-28; 122-project signed bundle captured only on loopback, not accepted by a Sonatype test service |
| Spring Cloud BOM resolution | Versionless Spring Cloud Alibaba Nacos Discovery consumer with `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies` | Required before Central deploy; rejects the unsupported Spring Boot 4.1.1 plus Spring Cloud 2025.1.2 combination |
| Quarkus JVM consumer smoke test | Install runtime/deployment artifacts, then `mvn -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pjvm-integration verify` | Historical PASS on Java 21; Java 25 revalidation is required by the release baseline |
| Helidon Native CDI/Web server consumer smoke test | GraalVM 25, `mvn -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pnative-image package`, then HTTP Problem Details smoke | PASS on 2026-08-25 with GraalVM Community 25.0.4 |
>>>>>>> 741d307a (fix(build): hand off Mason models to Quarkus tests)

JFoundry keeps exceptional overrides narrow and owned by the BOM for the affected ecosystem:

- The MyBatis-Plus Spring Boot starter owns its `org.mybatis:mybatis-spring` bridge version; JFoundry
  does not override that transitive dependency independently.
- Spring messaging uses Spring Boot's Jackson 3 line. Jackson 2 compatibility modules are outside the
  supported JFoundry stack.
- Foundation manages `org.javassist:javassist` because RocketMQ's transitive Reflections line otherwise
  selects an older POM that produces Maven 4 model warnings.
- `jfoundry-helidon-dependencies` aligns Jackson annotations with Foundation's Jackson 3 line and keeps
  the `groovy-all` override required by Maven release dependency validation. The owning BOM contains
  the exact override versions.

These exceptions are dependency-management decisions, not claims that JFoundry supplies an adapter for
every library managed by a runtime platform.

## Known Limitations

<<<<<<< HEAD
Maven 4 can report imported-BOM model warnings for supported runtime ecosystems. Release acceptance
requires successful package and consumer-POM resolution; it does not require a warning-free effective
model while Maven 4 remains experimental.

Helidon Native JTA and JPA are not supported. The Native CDI/Web consumer starts and serves JFoundry
Problem Details responses, but the transaction-manager delegate is not initialized for Native
`TransactionRunner` execution, and JPA image generation retains unsupported XML parser state. The JVM
PostgreSQL/JTA/JPA control result and Native failures are recorded in
[Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).

Helidon REST Client is JVM-only in the current acceptance scope. Its upstream Native Image substitution
contains an unannotated substitution method, so the Helidon Native smoke path excludes
`jfoundry-restclient-helidon` and does not claim Native REST Client support.

## Verification And Release Evidence

The authoritative verification definitions are in `.github/workflows/ci.yml`,
`scripts/verify-runtime-ci.sh`, and the owning runtime BOM POMs. They cover:

- Java 25 unit tests and artifact packaging;
- Spring, Quarkus, and Helidon middleware integration;
- Spring, Quarkus, and Helidon Native Image consumer checks;
- Maven 4 packaging, Consumer POM verification, and Maven 4.1 model/repository reachability checks;
- Mason YAML source, reactor reachability, and model-handoff checks;
- runtime BOM and supported-line consistency; exact versions are captured in CI logs and release
  evidence for each immutable commit or tag;
- release metadata and supply-chain policy.

For a released version, use the immutable tag's GitHub checks, release evidence artifact, and release
notes as the point-in-time PASS record. Do not infer release support from an older run or from a moving
SNAPSHOT branch.
=======
- `./mvnw test`
- `./mvnw -DskipTests package`
- `./mvnw -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify`
- Java 25 release-baseline test in CI
- Maven 4 Wrapper package compatibility and Consumer POM contract before Central deployment
- Mason source, model-equivalence, Quarkus model-handoff, and Maven 3 publication-bridge checks while the Mason PoC remains in the repository
- Spring and Quarkus Native Image smoke tests in CI
- Spring Native Image MyBatis-Plus persistence integration in CI
- Spring Native Image Redisson lock and JobRunr Outbox integrations in CI
- Quarkus and Helidon PostgreSQL middleware integration tests in CI
- Helidon Native CDI/Web smoke test with GraalVM 25; do not gate on Helidon Native JTA until its
  upstream implementation becomes supported
- Maven Central metadata guard in the `release` profile
>>>>>>> 741d307a (fix(build): hand off Mason models to Quarkus tests)
