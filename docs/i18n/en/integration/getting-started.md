# Getting Started

JFoundry's core modules do not depend on Spring, Quarkus, or Helidon. Start from business needs and
complete the domain and architecture decisions first, then select core capabilities and a runtime
integration at the application's outer edge.

## 1. Choose An Onboarding Path

Architecture style determines code boundaries, dependency direction, and the rules that architecture
tests enforce. It is a business-project decision, not a consequence of choosing Spring, Quarkus, or
Helidon.

For AI-assisted development, use
[`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills) and start
with `$domain-architecture-workflow`. This is the recommended path for non-trivial business projects.
The workflow starts from requirements and project evidence, performs domain modeling, decides
whether a full architecture style is justified, selects Hexagonal or Onion when appropriate, and
evaluates CQRS separately. It then produces traceable JFoundry landing guidance. Do not select the
architecture style before invoking the workflow.

For manual onboarding, make the architecture decision directly:

| Choice | Use when | JFoundry entry point |
|---|---|---|
| Hexagonal | Inputs, outputs, ports, and adapters need explicit direction. | `jfoundry-hexagonal` and `JFoundryRules.hexagonalStrict()` |
| Onion Simple | The main goal is inward dependencies and a protected domain core. | `jfoundry-onion` and `JFoundryRules.onionSimple()` |
| Onion Classical | The team deliberately needs finer domain model, domain service, and application service rings. | `jfoundry-onion` and `JFoundryRules.onionClassical()` |
| No full style yet | The scope is simple CRUD or a short-lived prototype with few business invariants. | Keep only the boundaries the project needs. |

After the AI-assisted or manual decision, protect the selected style with
`jfoundry-architecture-test`. Do not mix Hexagonal and Onion in the same analysis scope. CQRS is an
optional pattern for genuine command/query asymmetry, not a third primary style. See
[Architecture Styles](../framework/architecture-styles.md) for the complete manual decision guide.

## 2. Configure The Core BOM And Architecture Dependencies

Every external application must have `jfoundry-dependencies` in its dependency management. It is the
public JFoundry BOM for core modules, architecture modules, and framework-neutral adapters. The
supported `jfoundry-spring-boot-parent` imports it for applications that do not use Spring Cloud; other applications
must import it explicitly. It belongs in `<dependencyManagement>` and is not a runtime dependency:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add dependencies by responsibility:

| Responsibility | Starting dependency |
|---|---|
| Domain model | `jfoundry-domain` |
| Application services | `jfoundry-application-core` |
| Primary architecture style | Choose one: `jfoundry-hexagonal` or `jfoundry-onion` |
| Architecture verification | `jfoundry-architecture-test` in test scope |
| Technical implementation | Select framework-neutral JPA, MyBatis-Plus, messaging, or other adapters as needed |

For example, a Hexagonal project directly depends on its domain module and architecture facade, then
uses the architecture rules in test scope. Replace `jfoundry-hexagonal` with `jfoundry-onion` for an
Onion project:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-hexagonal</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-architecture-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Domain and application modules must not depend on a concrete runtime. Persistence, messaging,
Outbox, Inbox, and other capabilities also remain explicit choices rather than implicit base
dependencies.

## 3. Select A Runtime

Each runtime has its own BOM and base entry point; do not mix runtime BOMs. When an application keeps
its own Maven parent, import the matching runtime BOM before `jfoundry-dependencies`; the supported
JFoundry Boot parent manages both for Spring Boot applications that do not use Spring Cloud. See the runtime guide for its Parent,
BOM, and dependency composition.

| Runtime | Base entry point | Setup guide |
|---|---|---|
| Spring Boot | Select the required Spring Boot capability starter; the shared baseline is usually transitive | [Spring Boot Runtime Assembly](../implementations/spring-boot.md) |
| Quarkus | `jfoundry-quarkus-runtime` | [Quarkus Runtime Integration](../implementations/quarkus.md) |
| Helidon MP | `jfoundry-helidon-runtime` | [Helidon MP Runtime Integration](../implementations/helidon.md) |

Runtime entry points assemble capabilities; they do not belong in domain or application code.

## 4. Add Capabilities As Needed

Use the [Capability Catalog](../capabilities/index.md) to select aggregate persistence, transactions,
Web, messaging, Outbox/Inbox, distributed locks, or observability. Each capability page lists the
matching entry points and current support scope for every runtime.

Before implementation, continue with
[Repository and Read-side Contracts](../modeling/repository-vs-read-contracts.md). Before production
adoption, review [Adoption Readiness and Validated Scope](adoption-readiness.md).
