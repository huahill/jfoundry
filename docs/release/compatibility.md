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
| Maven source descriptor model | 4.1.0 (Maven 4-only XML model) |
| Maven wrapper and Consumer POM verification | 4.0.0-rc-6 (Maven 4-only model/build validation) |
| Maven Central deploy runtime | Apache Maven 3.9.16 |
| Maven 3 source-build compatibility | Not supported; deploy-only compatibility runtime |

## Consumer Composition

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

This matrix does not certify arbitrary downstream dependency graphs, databases, brokers, deployment
targets, or application configuration. Consumers must run acceptance tests for their selected
capability set and operational environment.

## Managed Compatibility Exceptions

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

Maven 4 can report imported-BOM model warnings for supported runtime ecosystems. Release acceptance
requires successful package and consumer-POM resolution; it does not require a warning-free effective
model while Maven 4 remains experimental.

Quarkus 3.39.1's test bootstrap cannot currently load the Maven 4.1 `subprojects` workspace model,
so the Quarkus CDI unit-test stage remains blocked by
[Quarkus issue #56270](https://github.com/quarkusio/quarkus/issues/56270) until its Maven 4 support
is released. This does not affect Maven 4 packaging or the other runtime verification stages.

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
- runtime BOM and supported-line consistency; exact versions are captured in CI logs and release
  evidence for each immutable commit or tag;
- release metadata and supply-chain policy.

For a released version, use the immutable tag's GitHub checks, release evidence artifact, and release
notes as the point-in-time PASS record. Do not infer release support from an older run or from a moving
SNAPSHOT branch.
