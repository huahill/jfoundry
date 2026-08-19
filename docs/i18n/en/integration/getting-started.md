# Getting Started

Start with the smallest architecture that serves the current business use case. jfoundry is most
useful when a system needs explicit aggregates, invariants, domain events, architecture boundaries,
or reliable external integration. A short CRUD prototype without those needs may be simpler with a
plain runtime framework and ORM.

## Choose A Parent, BOM, And Module Boundary

Choose one Spring platform line. The lines are intentionally incompatible and an application must not
import both runtime BOMs.

| Line | Parent | Runtime BOM | Platform baseline |
|------|--------|-------------|-------------------|
| Boot-only | `jfoundry-spring-boot-parent` | `jfoundry-spring-boot-dependencies` | Spring Boot 4.1.0 |
| Cloud | `jfoundry-spring-cloud-parent` | `jfoundry-spring-cloud-dependencies` | Spring Boot 4.0.7, Spring Cloud 2025.1.2, Spring Cloud Alibaba 2025.1.0.0 |

For a Boot-only application, use `jfoundry-spring-boot-parent` as the only Maven parent:

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>1.0.3</version>
</parent>
```

It inherits `spring-boot-starter-parent:4.1.0`, sets the Java 25 baseline, and imports the Boot-only
runtime BOM before `jfoundry-dependencies`. Declare Spring Boot and JFoundry dependencies without a
version, but continue to select each JFoundry capability starter explicitly. A Cloud application uses
`jfoundry-spring-cloud-parent`, which inherits Spring Boot 4.0.7 and imports the Cloud runtime BOM.

An application that must keep a different Maven parent imports `jfoundry-dependencies` for JFoundry
module versions. An application that uses a supported runtime additionally imports exactly one
matching runtime BOM: `jfoundry-spring-boot-dependencies`,
`jfoundry-spring-cloud-dependencies`, `jfoundry-quarkus-dependencies`, or
`jfoundry-helidon-dependencies`. Runtime BOMs manage only their platform ecosystems; they do not
replace the core JFoundry BOM. Import the runtime BOM before `jfoundry-dependencies` so the runtime
platform's managed constraints take precedence. Select versions from the intended release line; this
project currently uses the following development version.

The Boot-only BOM manages only Spring Boot. The Cloud BOM manages Spring Boot, Spring Cloud, and
Spring Cloud Alibaba as one tested platform line. This lets a Cloud application add a selected Cloud
starter without a version; it does not add any Cloud starter automatically or imply that JFoundry
provides an adapter for that starter.

The following XML is the alternative for a Boot-only application that cannot use the JFoundry parent.
For the Cloud line, replace the first import with `jfoundry-spring-cloud-dependencies`. For Quarkus
or Helidon, use the matching runtime BOM and retain the second core BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-boot-dependencies</artifactId>
            <version>1.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>1.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Keep dependencies in the layer that owns them:

| Module | Starting dependency |
|--------|---------------------|
| Domain | `jfoundry-domain-starter` |
| Application | `jfoundry-application-starter` |
| Infrastructure | The selected runtime-neutral capability starter, such as `jfoundry-persistence-jpa-starter` |
| Spring Boot assembly | `jfoundry-spring-boot-starter` plus only the required runtime capability starters |
| Quarkus runtime integration | `jfoundry-quarkus-runtime` |
| Helidon MP runtime integration | `jfoundry-helidon-runtime` |

Choose Hexagonal or Onion from domain and project constraints; jfoundry does not select an
architecture style for a business project. Add ArchUnit tests before implementation grows around
accidental dependencies.

## Assemble A Minimal Spring Boot Runtime

For a Spring Boot application using JPA business persistence, the runtime module starts with the
base and JPA runtime starters:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-persistence-jpa-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Configure the application's datasource and keep its persistence adapter in the infrastructure
module. The JPA runtime starter does not add Outbox or Inbox stores. For MyBatis-Plus persistence,
replace the JPA runtime starter with `jfoundry-persistence-mybatis-plus-spring-boot-starter`; it
also leaves Outbox and Inbox stores explicit. See [JPA](../implementations/jpa.md),
[MyBatis-Plus](../implementations/mybatis-plus.md), and
[Spring Boot Runtime Assembly](../implementations/spring-boot.md) for the exact implementation
boundaries.

## Add Capabilities Only When Needed

- Start with the [Capability Catalog](../capabilities/index.md). It maps a business need to the
  supported runtime entry point and links to the contract and composition guidance.
- Add [Application Transactions](../capabilities/application-transactions.md) or
  [Distributed Locks](../capabilities/distributed-locks.md) when the use case needs them.
- Add [Message Delivery](../capabilities/message-delivery.md) when the application needs a direct
  broker producer; add [Reliable Messaging: Outbox And Inbox](../capabilities/reliable-messaging.md)
  only when it also needs durable publication or consumer idempotency. Add Web MVC and scheduling
  starters only for their corresponding capability.

The [Spring Boot Auto-configuration reference](../reference/spring-boot-autoconfiguration.md)
details individual Spring starters, properties, and registration conditions.

For runtime-specific dependency setup, composition, and verification, see
[Spring Boot Runtime Assembly](../implementations/spring-boot.md),
[Quarkus Runtime Integration](../implementations/quarkus.md), and
[Helidon MP Runtime Integration](../implementations/helidon.md).

## Reading Path

1. Define boundaries with [Architecture Styles](../framework/architecture-styles.md) and
   [ArchUnit Architecture Rules](../framework/archunit-rules.md).
2. Model aggregates and choose repository/read-side contracts with
   [Repository and Read-side Contracts](../modeling/repository-vs-read-contracts.md).
3. Apply [Aggregate Persistence](../capabilities/aggregate-persistence.md) through the selected
   implementation.

See [Adoption Readiness and Validated Scope](adoption-readiness.md) before relying on a capability
in production.
