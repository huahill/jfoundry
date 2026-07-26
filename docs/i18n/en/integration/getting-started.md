# Getting Started

Start with the smallest architecture that serves the current business use case. jfoundry is most
useful when a system needs explicit aggregates, invariants, domain events, architecture boundaries,
or reliable external integration. A short CRUD prototype without those needs may be simpler with a
plain runtime framework and ORM.

## Choose A BOM And Module Boundary

Every application imports `jfoundry-dependencies` for JFoundry module versions. An application that
uses a supported runtime additionally imports exactly one matching runtime BOM:
`jfoundry-spring-dependencies`, `jfoundry-quarkus-dependencies`, or
`jfoundry-helidon-dependencies`. Runtime BOMs manage only their platform ecosystems; they do not
replace the core JFoundry BOM. Select versions from the intended release line; this project currently
uses the following development version.

Runtime BOMs may also manage official Cloud or integration BOMs compatible with their platform
baseline. For example, the Spring runtime BOM manages the aligned Spring Boot, Spring Cloud, and
Spring Cloud Alibaba version lines. This lets an application add a selected Cloud starter without a
version; it does not add any Cloud starter automatically or imply that JFoundry provides an adapter
for that starter.

The following XML uses Spring Boot as the runtime example. For Quarkus or Helidon, retain the core
BOM and replace the second import with the matching runtime BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
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
| Infrastructure | The selected runtime-neutral capability starter, such as `jfoundry-persistence-mybatis-plus-starter` |
| Spring Boot assembly | `jfoundry-spring-boot-starter` plus only the required runtime capability starters |
| Quarkus runtime integration | `jfoundry-quarkus-runtime` |
| Helidon MP runtime integration | `jfoundry-helidon-runtime` |

Choose Hexagonal or Onion from domain and project constraints; jfoundry does not select an
architecture style for a business project. Add ArchUnit tests before implementation grows around
accidental dependencies.

## Assemble A Minimal Spring Boot Runtime

For a Spring Boot application using business MyBatis-Plus persistence, the runtime module starts
with the base and MyBatis-Plus runtime starters:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-persistence-mybatis-plus-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Configure the application's datasource and keep its persistence adapter in the infrastructure
module. The MyBatis-Plus runtime starter does not add Outbox or Inbox stores. For a JPA runtime,
replace the MyBatis-Plus runtime starter with `jfoundry-persistence-jpa-spring-boot-starter`; it also leaves
Outbox and Inbox stores explicit. See [MyBatis-Plus](../implementations/mybatis-plus.md),
[JPA](../implementations/jpa.md), and [Spring Boot Runtime Assembly](../implementations/spring-boot.md)
for the exact implementation boundaries.

## Add Capabilities Only When Needed

- Add [Application Transactions](../capabilities/application-transactions.md) or
  [Distributed Locks](../capabilities/distributed-locks.md) when the use case needs them.
- Add [Reliable Messaging: Outbox And Inbox](../capabilities/reliable-messaging.md) only for
  cross-process delivery or consumer idempotency; select its store explicitly.
- Add messaging, broker, Web MVC, and scheduling starters only for their corresponding capability.

The [Spring Boot Auto-configuration reference](../reference/spring-boot-autoconfiguration.md) is
the canonical catalog for individual starters, properties, and registration conditions.

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
