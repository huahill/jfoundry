# jfoundry

English | [中文](README_ZH.md)

---

`jfoundry` is a practical DDD framework for Java, built on jMolecules and designed for Hexagonal Architecture and Onion Architecture.

It helps business projects make domain modeling, architecture boundaries, and reliable integration executable in code. The core defines DDD concepts, architecture semantics, application contracts, domain events, persistence SPI, and messaging SPI without depending on a runtime framework. Spring, Quarkus, and Helidon assemble the same core through peer runtime integration modules.

## Why jfoundry

DDD projects often lose their intended boundaries in implementation: domain code imports framework or ORM APIs, transaction ownership is unclear, repositories become generic query interfaces, and external events are not delivered reliably. `jfoundry` provides:

- jMolecules-based DDD, Hexagonal, Onion, and CQRS semantics.
- Explicit dependency direction across domain, application, infrastructure, and runtime integration.
- Reusable ArchUnit rules for executable architecture constraints.
- Optional production capabilities for persistence, message delivery, reliable messaging, transactions, and runtime assembly.

## Architecture

The domain model stays independent of Spring, ORM, HTTP, brokers, and database clients. Application contracts orchestrate use cases and define capability SPI; infrastructure implements technical adapters; runtime integrations assemble them.

```text
runtime integration
  -> application / infrastructure adapters
       -> application contracts
            -> domain
```

Dependencies point inward. This keeps runtime integrations outside the core rather than making a particular framework a requirement for every application.

![jfoundry module architecture](docs/i18n/assets/jfoundry-module-architecture.svg)

## AI-Assisted Architecture Workflow

[`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills) is the
Domain Architecture Plugin that complements JFoundry at design time. It guides an AI coding agent
from requirements through domain modeling and architecture decisions, then enters JFoundry only
when JFoundry is selected. It is installed separately and is not a runtime dependency or a
mandatory combined architecture.

See [Adoption Readiness and Validated Scope](docs/i18n/en/integration/adoption-readiness.md) for
the evidence and boundaries of using the plugin together with JFoundry.

```text
requirements -> domain modeling -> architecture decision -> optional jfoundry landing -> implementation handoff
```

## Capabilities

| Area | Capability |
|------|------------|
| Domain modeling | Aggregates, value objects, domain events, repository contracts, and domain exceptions |
| Architecture | Hexagonal and Onion semantics with ArchUnit rules |
| Application | Application services, transaction boundaries, CQRS, and domain-event orchestration |
| Persistence | Aggregate persistence contracts with JPA and MyBatis-Plus implementations |
| Message delivery | Runtime-neutral outbound transport contracts with explicit Kafka, RabbitMQ, and RocketMQ adapters |
| Reliable messaging | Transactional Outbox, Inbox idempotency, messaging, and serialization SPI |
| Runtime integration | Spring Framework and Spring Boot assembly; Quarkus and Helidon CDI/Jakarta Transactions, JPA, Outbox/Inbox, and REST Problem Details assembly |

## Choose Your Path

- **Architecture and modeling**: start with [Getting Started](docs/i18n/en/integration/getting-started.md), then select an [architecture style](docs/i18n/en/framework/architecture-styles.md) and review [modeling conventions](docs/i18n/en/modeling/repository-vs-read-contracts.md).
- **Aggregate persistence**: read [Aggregate Persistence](docs/i18n/en/capabilities/aggregate-persistence.md), then choose the peer implementation that fits the project: [JPA](docs/i18n/en/implementations/jpa.md) or [MyBatis-Plus](docs/i18n/en/implementations/mybatis-plus.md).
- **Message delivery**: read [Message Delivery](docs/i18n/en/capabilities/message-delivery.md) to select a direct Kafka, RabbitMQ, RocketMQ, or application-owned transport adapter.
- **Reliable messaging**: read [Reliable Messaging](docs/i18n/en/capabilities/reliable-messaging.md), then choose its JPA or MyBatis-Plus store from the corresponding [JPA](docs/i18n/en/implementations/jpa.md) or [MyBatis-Plus](docs/i18n/en/implementations/mybatis-plus.md) guide.
- **Spring Boot**: use [Spring Boot Runtime Assembly](docs/i18n/en/implementations/spring-boot.md) for starter-based, conditional auto-configuration of selected capabilities; see the [Spring Boot auto-configuration reference](docs/i18n/en/reference/spring-boot-autoconfiguration.md) for its properties, conditions, and bean precedence.
- **Quarkus**: use [Quarkus Runtime Integration](docs/i18n/en/implementations/quarkus.md) for explicit extension composition, CDI transactions, REST Problem Details, domain-event dispatch, JPA-backed reliable messaging, Kafka and RabbitMQ delivery, and Native Image verification.
- **Helidon MP**: use [Helidon MP Runtime Integration](docs/i18n/en/implementations/helidon.md) for explicit CDI/JTA, JPA, Outbox/Inbox, and REST Problem Details composition. Its Native Image support currently verifies CDI/Web only; Helidon Narayana JTA Native execution remains experimental upstream.

## Minimal Setup

Every application imports the runtime-neutral BOM, then adds only the starters and capability implementations it requires. A Spring Boot, Quarkus, or Helidon application additionally imports its matching runtime BOM; runtime BOMs manage their platform ecosystems and do not replace the JFoundry BOM.

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

## Domain Model Example

```java
// Money.java
import org.jfoundry.domain.valueobject.ValueObject;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) implements ValueObject {
}
```

```java
// OrderId.java
import org.jmolecules.ddd.types.Identifier;

public record OrderId(String value) implements Identifier {
}
```

```java
// Order.java
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;

public final class Order extends BaseAggregateRoot<Order, OrderId> {

    private Money total;

    public Order(OrderId id, Money total) {
        super(id);
        this.total = total;
    }

    public void changeTotal(Money total) {
        this.total = total;
    }
}
```

## Documentation

### Getting Started

- [Getting Started](docs/i18n/en/integration/getting-started.md)
- [Adoption Readiness and Validated Scope](docs/i18n/en/integration/adoption-readiness.md)
- [`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills)

### Capabilities

- [Aggregate Persistence](docs/i18n/en/capabilities/aggregate-persistence.md)
- [Message Delivery](docs/i18n/en/capabilities/message-delivery.md)
- [Reliable Messaging: Outbox And Inbox](docs/i18n/en/capabilities/reliable-messaging.md)
- [Application Transactions](docs/i18n/en/capabilities/application-transactions.md)
- [Distributed Locks](docs/i18n/en/capabilities/distributed-locks.md)

### Persistence Implementations

- [JPA](docs/i18n/en/implementations/jpa.md)
- [MyBatis-Plus](docs/i18n/en/implementations/mybatis-plus.md)

### Runtime Integrations

- [Spring Boot Runtime Assembly](docs/i18n/en/implementations/spring-boot.md)
- [Quarkus Runtime Integration](docs/i18n/en/implementations/quarkus.md)
- [Helidon MP Runtime Integration](docs/i18n/en/implementations/helidon.md)

### Framework Semantics

- [Architecture Styles](docs/i18n/en/framework/architecture-styles.md)
- [ArchUnit Architecture Rules](docs/i18n/en/framework/archunit-rules.md)
- [Framework Boundaries](docs/i18n/en/framework/framework-boundaries.md)

### Modeling

- [Value Object Guide](docs/i18n/en/modeling/value-object.md)
- [Repository and Read-side Contracts](docs/i18n/en/modeling/repository-vs-read-contracts.md)

### Release and Compatibility

- [Compatibility Matrix](docs/release/compatibility.md)
- [Maven Central Publishing](docs/release/maven-central.md)
- [Native Image verification status](docs/i18n/en/integration/adoption-readiness.md): review the scoped runtime and capability evidence together with the [Compatibility Matrix](docs/release/compatibility.md); it is not a blanket support claim.

For the complete documentation structure, see the [Documentation Index](docs/i18n/en/index.md).

## Build

```bash
mvn validate
mvn test
mvn clean install
```

## License

[Apache License 2.0](LICENSE)
