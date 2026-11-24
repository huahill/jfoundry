# Getting Started

JFoundry keeps domain, application, and architecture choices independent from the application runtime. Use this path to choose the boundaries first, then select one runtime and the capabilities it needs.

## 1. Choose the architecture style

Choose Hexagonal, Onion Simple, Onion Classical, or no full style according to the business project's needs. Do not mix Hexagonal and Onion in one analysis scope; CQRS is an optional pattern. Use `jfoundry-architecture-test` to verify the chosen style. See [Architecture Styles](../framework/architecture-styles.md).

For AI-assisted development, the optional [`domain-architecture-skills`](https://github.com/huahill/domain-architecture-skills) workflow can make the same decision from requirements and project evidence.

## 2. Add the core BOM

Every application uses `jfoundry-dependencies` for DDD, architecture styles, application contracts, and framework-neutral adapters. The Spring Boot parent includes it through the Boot consumer BOM; other applications import it explicitly with their runtime BOM.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.huahill</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Select `jfoundry-domain`, `jfoundry-application-core`, one architecture-style module, and `jfoundry-architecture-test` as needed. Persistence, messaging, Outbox, Inbox, and locks remain explicit capabilities.

## 3. Choose a runtime

| Runtime | Consumer entry point | Guide |
|---|---|---|
| Spring Boot | `jfoundry-spring-boot-parent` or `jfoundry-spring-boot-dependencies` | [Spring Boot](../runtimes/spring-boot.md) |
| Quarkus | `jfoundry-dependencies` plus `jfoundry-quarkus-dependencies` | [Quarkus](../runtimes/quarkus.md) |
| Helidon MP | `jfoundry-dependencies` plus `jfoundry-helidon-dependencies` | [Helidon MP](../runtimes/helidon.md) |

Choose one runtime path. Runtime modules stay outside domain and application code.

## 4. Select capabilities

Start from the [Capability Catalog](../capabilities/index.md), then choose the matching runtime entry point and persistence implementation. Use [Persistence Implementations](../persistence/index.md) for JPA and MyBatis-Plus details. Before production adoption, review [Adoption Readiness and Validated Scope](adoption-readiness.md).
