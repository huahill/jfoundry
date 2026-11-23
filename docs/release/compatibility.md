# Compatibility Matrix

This document defines the supported platform lines, validated capability scope, and known limitations
for the current JFoundry development line. Runtime BOM POMs are the executable source of exact
platform versions. Spring Boot-only follows the current stable Spring Boot line and is validated by the
full CI matrix on every bump, so no Boot line is pinned here. The table below records the composite
platform lines and their owning BOMs; `scripts/verify-compatibility-matrix.sh` keeps those boundaries
aligned with the BOMs without coupling documentation to patch-level updates.

## Runtime Platform Baselines

| Platform | Supported line | Version source |
|----------|----------------|----------------|
| Spring Cloud | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Spring Cloud Alibaba | 2025.1.x | `jfoundry-spring-cloud-dependencies` |
| Quarkus | 3.40.x | `jfoundry-quarkus-dependencies` |
| Helidon MP | 4.5.x | `jfoundry-helidon-dependencies` |

Spring Cloud follows the upstream train, and a Cloud application pairs it with whatever Spring Boot
line that train supports; the Cloud BOM deliberately does not manage Spring Boot, so the application
parent remains the version source for that part of the combination. Spring Cloud Alibaba (the Nacos
ecosystem) publishes behind the Spring Cloud train, so a new Spring Cloud line is adopted together
with its matching Alibaba train — the two rows above move in one change, which is what the
verification below enforces. Boot-only applications use the current stable Spring Boot line and must
not combine the Boot-only and Cloud runtime BOMs.

Helidon's upstream line is JDK-aligned: Tip releases at major 27 and later require the JDK their
major number names, while Helidon 4.5 remains the supported long-term line. The Java 25 baseline
therefore stays on Helidon MP 4.5.x, and `.github/dependabot.yml` ignores
`io.helidon:helidon-dependencies` versions `>= 27` so Tip releases are not proposed. Raise or remove
that ignore floor in the same change that raises the Java baseline.

## Toolchain Baselines

| Area | Supported baseline |
|------|--------------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Native Image | GraalVM 25 |
| Maven source descriptor model | 4.1.0 (Maven 4-only XML model) |
| Maven wrapper and Consumer POM verification | 4.0.0-rc-7 (Maven 4-only model/build validation) |
| Maven Central deploy runtime | Apache Maven 3.9.16 |
| Maven 3 source-build compatibility | Not supported; deploy-only compatibility runtime |
| Maven 3 publication-tree conversion | Python 3 standard library; disposable release-only tree |

## Consumer Composition

Every business application imports `jfoundry-dependencies` and adds only the runtime BOM and
capabilities it needs:

- Spring Boot-only applications use `jfoundry-spring-boot-dependencies` or
  `jfoundry-spring-boot-parent`.
- Spring Cloud applications use their own compatible Spring Boot parent and import
  `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies`.
- Quarkus and Helidon applications import their matching runtime BOM before
  `jfoundry-dependencies`.

Runtime BOMs manage their platform ecosystem and matching JFoundry runtime module versions. They do
not add runtime capabilities to the classpath or import Foundation. Foundation supplies only
runtime-neutral coordinates the platform BOM does not already own. Applications still select each
starter, adapter, database, and broker explicitly.

The aggregate `jfoundry-dependencies` BOM manages framework-neutral JFoundry modules. Spring Boot,
Quarkus, and Helidon BOMs manage their matching JFoundry runtime modules; the shared
`jfoundry-jakarta-dependencies` BOM is imported by the Quarkus and Helidon BOMs for portable Jakarta
adapters. These management entries do not add capabilities to the application classpath.

## Validated Capability Scope

The Merge gate requires the current commit's applicable JVM, middleware, Native Image, packaging, and
consumer-POM jobs to succeed. The workflow result for that commit is the current evidence; this page
describes the stable scope those jobs cover rather than copying transient PASS or pending states.

| Runtime | JVM and middleware scope | Native Image scope |
|---------|--------------------------|--------------------|
| Spring | Runtime assembly plus PostgreSQL, MySQL, Kafka, RabbitMQ, RocketMQ, Redis/Redisson, MyBatis-Plus, JPA, Outbox, and Inbox integration paths | Base runtime and Web MVC plus MyBatis-Plus/PostgreSQL and Redisson/Redis capability checks |
| Quarkus | CDI, JTA, JPA, REST, messaging, Outbox/Inbox, PostgreSQL, and Redis/Redisson runtime wiring | JPA aggregate persistence, transactions, domain events, Outbox, and Inbox against PostgreSQL, plus a separate Redisson/Redis capability check |
| Helidon MP | CDI, JTA, JPA, REST, Outbox/Inbox, scheduling, Problem Details, PostgreSQL, and Redis/Redisson runtime wiring | CDI/Web startup and Problem Details response only |

This matrix does not certify arbitrary downstream dependency graphs, databases, brokers, deployment
targets, or application configuration. Consumers must run acceptance tests for their selected
capability set and operational environment.

## Managed Compatibility Exceptions

JFoundry keeps exceptional overrides narrow and owned by the BOM for the affected ecosystem. This
section records each override's rationale at line level; exact override versions live in the owning
BOM or build parent, and exact per-release versions live in the CI logs and release evidence.

- The MyBatis-Plus Spring Boot starter owns its `org.mybatis:mybatis-spring` bridge version; JFoundry
  does not override that transitive dependency independently.
- Spring messaging uses Spring Boot's Jackson 3 line. Jackson 2 compatibility modules are outside the
  supported JFoundry stack.
- Foundation manages `org.javassist:javassist` because RocketMQ's transitive Reflections line otherwise
  selects an older POM that produces Maven 4 model warnings.
- `jfoundry-quarkus-dependencies` keeps a Quarkus-local `jackson-annotations` override; the owning BOM
  contains the exact override version. Quarkus 3.40.x manages the Jackson 2 annotation line at 2.21,
  but Foundation's Jackson 3.2 line requires `com.fasterxml.jackson.core:jackson-annotations` 2.22,
  including `JsonApplyView`. The override is declared before the Quarkus BOM import because Maven
  keeps the first managed coordinate.
- `jfoundry-helidon-dependencies` keeps a Helidon-local Jackson annotations override and the
  `groovy-all` override required by Maven release dependency validation. The owning BOM contains
  the exact override versions.
- `jfoundry-helidon-dependencies` also pins `org.testcontainers:testcontainers` to the
  Foundation-managed 2.0 line. Helidon 4.5.x manages the 1.21 line, whose core brings `junit:junit`
  and makes Surefire treat JUnit Jupiter tags as JUnit 4 categories. This override keeps the shared
  core coordinate on the Foundation line.
- The Helidon reactor directly manages `hibernate-core`, `jakarta.persistence-api`, and
  `jboss-logging` ahead of the Helidon BOM import; the Helidon build parent contains the exact
  override versions. Helidon 4.5.x would otherwise select the Hibernate 6.3 / JPA 3.1 /
  jboss-logging 3.5 line. The override is build-local and is not published in
  `jfoundry-helidon-dependencies`.

These exceptions are dependency-management decisions, not claims that JFoundry supplies an adapter for
every library managed by a runtime platform.

## Known Limitations

Maven 4 can report imported-BOM model warnings for supported runtime ecosystems. Release acceptance
requires successful package and consumer-POM resolution; it does not require a warning-free effective
model while Maven 4 remains experimental.

Maven 4.0.0-rc-7 quiet mode writes the distribution version to standard output before a forced
expression result. Release and snapshot workflows resolve `project.version` through
`scripts/maven-project-version.sh`, which ignores that line and the older `[INFO] [stdout]` prefix.

Quarkus 3.40.x test bootstrap still cannot load the Maven 4.1 `subprojects` workspace model on
its own. JFoundry keeps `quarkus-maven-plugin:generate-code-tests` on the affected `@QuarkusTest`
modules, the workaround accepted when
[Quarkus issue #56270](https://github.com/quarkusio/quarkus/issues/56270) was closed. Upstream Maven 4
POM loading is not part of this Quarkus line. This does not affect Maven 4 packaging or the other
runtime verification stages.

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
