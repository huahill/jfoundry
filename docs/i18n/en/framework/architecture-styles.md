# Architecture Styles

jfoundry expresses two primary architecture styles through jMolecules: Hexagonal Architecture
(Ports and Adapters) and Onion Architecture. Both are pure Java annotation modules and do not
depend on Spring, Helidon, Quarkus, Micronaut, or other runtime frameworks.

jfoundry internals use Onion Simple to keep module roles consistent. Business applications can
choose either Hexagonal or Onion based on their architecture language.

## Module Choice

| Module | Use when |
|--------|----------|
| `jfoundry-hexagonal` | The project wants explicit primary/secondary ports and adapters. |
| `jfoundry-onion` | The project wants dependency direction to converge toward the domain model. |

`jfoundry-architecture` is an aggregate POM for architecture style modules. Business code should
normally depend on the concrete style module or a starter that brings it in.

## Hexagonal

Hexagonal Architecture is useful when external inputs and outputs are first-class design concepts.

| Role | Typical location | Meaning |
|------|------------------|---------|
| `@Application` | `domain`, `application` | Application core protected from external technologies. |
| `@PrimaryPort` | `application.port.in` | Use case entrypoint exposed to driving adapters. |
| `@PrimaryAdapter` | `adapter.in.web`, `adapter.in.messaging` | Technology that drives the application. |
| `@SecondaryPort` | `application.port.out`; `domain.repository` for aggregate repositories | Capability the application requires from the outside. |
| `@SecondaryAdapter` | `adapter.out.persistence`, `adapter.out.messaging` | Technology implementation of an outgoing port. |

jfoundry intentionally does not wrap generic `@Port` or `@Adapter`; use the primary/secondary
specializations when direction is known.

An aggregate repository remains a DDD repository contract. In a Hexagonal project it may also be
annotated as a `@SecondaryPort`, while remaining under `domain.repository`; do not duplicate it as
an application `port.out` interface. A `@SecondaryAdapter` may implement either a regular secondary
port or such a DDD repository.

Hexagonal Architecture does not mandate a package tree. Small projects may use global
`application.port.in` and `application.port.out` packages. For non-trivial business applications,
jfoundry recommends organizing by business capability first and locating direction inside it, for
example `application.claim.query.port.in`, `application.claim.query.port.out`, and
`application.claim.query.view`. The strict convention rules recognize direction packages at any
depth.

Primary and Secondary Ports may share application-owned query or view models, but neither direction
should own models exposed by the other. Put shared models in a neutral application capability
package: Primary Ports must not depend on `port.out` packages, and Secondary Ports must not depend
on `port.in` packages. Keep HTTP DTOs in the primary adapter and persistence/remote data models in
the secondary adapter.

For a CQRS command exposed to a driving adapter, use a business-named `*UseCase` as the
`@PrimaryPort` and let its command handler implement that contract. The command is its input model;
the handler is the application implementation. A fixed `*CommandDispatcher` with overloaded
`dispatch(...)` methods that only delegates to statically known handlers adds neither routing nor
business orchestration. Reserve a dispatcher or command bus for genuine runtime routing through a
shared pipeline, such as pluggable handler registration or generic message transport.

When CQRS builds or refreshes a derived read model from an event or state change, the contract for
that projection materialization may be a Secondary Port and its technology implementation a
Secondary Adapter. It is distinct from a read-only query contract: a `Reader` reads the model,
whereas a `ProjectionStore` materializes or updates a derived read model from facts already decided
by a command or event. It does not re-decide business rules or modify an aggregate. When technical
grouping helps, use `adapter.out.query.<feature>` for a
Reader and `adapter.out.projection.<feature>` for the materializer. `projection` is optional
terminology, not a universal package or suffix, and does not imply Event Sourcing.

`adapter.out.lookup.<feature>` is for a read-only fact needed by command workflow or a domain
decision. It differs from `adapter.out.query.<feature>`, which serves caller-facing pages, lists,
reports, exports, or other query use cases, even though both may execute a database read.

![hexagonal-architecture.png](../../assets/hexagonal-architecture.png)

## Onion

Onion Architecture is useful when the main goal is protecting the domain core and making dependency
direction explicit.

| Style | Role | Typical location |
|-------|------|------------------|
| Onion Simple | `@DomainRing` | `domain` |
| Onion Simple | `@ApplicationRing` | `application` |
| Onion Simple | `@InfrastructureRing` | `infrastructure`, `adapter` |
| Onion Classical | `@DomainModelRing` | `domain.model` |
| Onion Classical | `@DomainServiceRing` | `domain.service` |
| Onion Classical | `@ApplicationServiceRing` | `application` |
| Onion Classical | `@InfrastructureRing` | `infrastructure` |

For new projects that choose Onion, prefer Onion Simple unless the team already has a clear reason
to separate domain model, domain service, and application service rings.

In Onion, the same aggregate repository is an inner-ring DDD contract implemented by an
`@InfrastructureRing` type. This is the Onion expression of dependency inversion; do not add
Hexagonal annotations to an Onion analysis scope.

Onion Architecture does not define Primary/Secondary Port or Adapter roles and does not prescribe
`*Port`, `*Adapter`, or `*UseCase` suffixes. Name domain types from the ubiquitous language and name
application contracts by their actual responsibility. Names such as `Reader`, `Store`, `Finder`, or
`Provider` are clear Java project conventions when they fit the responsibility; they are not DDD or
Onion patterns and jfoundry does not require them. Infrastructure implementations may add technology
names such as `Mybatis` or `Kafka` where that identifies the implementation.

Within an Onion ring, non-trivial code may still be organized by business capability first. Models
shared by application contracts belong to a neutral package inside that application capability,
not to the domain or infrastructure merely for reuse. This ownership rule does not introduce
`port.in` / `port.out` semantics into Onion.

For Onion CQRS commands, use a business-named application handler or service and let outer-ring
web, messaging, or scheduler code call that application boundary. CQRS does not introduce
Hexagonal `port.in`, `@PrimaryPort`, or `*UseCase` requirements into Onion. A dispatcher still
needs a genuine runtime-routing reason; fixed forwarding is unhelpful here as well.

When CQRS materializes or updates a derived read model from facts already decided by a command or
event, the inner ring owns the needed contract and an `@InfrastructureRing` type implements it. It
does not re-decide business rules or modify an aggregate. Onion does not thereby gain Hexagonal
port or adapter roles. Keep this materializer distinct from a read-only `Reader`; where technical
grouping helps, infrastructure may use `query.<feature>` for the Reader and
`projection.<feature>` for the materializer. This is optional project terminology and does not
require Event Sourcing.

Similarly, use `infrastructure.lookup.<feature>` for command-side fact reads and
`infrastructure.query.<feature>` for caller-facing read use cases when this technical grouping
improves navigation. The two roles are both read-only, but have different consumers and change
reasons.

![onion-architecture.png](../../assets/onion-architecture.png)

### How To Choose

| Perspective | Better fit | Simple example |
|---|---|---|
| Hexagonal | External inputs and outputs need explicit driver, port, and adapter roles. | An order system receives REST commands, persists aggregates, publishes Kafka events, and calls a payment SDK. |
| Hexagonal | An existing controller-service-mapper design is being separated into primary ports, secondary ports, and adapters. | A controller calls a primary port, the application calls a secondary port, and a MyBatis adapter implements it. |
| Hexagonal | Reliable event externalization treats Outbox storage, `MessageSender`, and broker delivery as external capabilities. | Creating an order records an Outbox message that a Kafka sender later delivers. |
| Onion | The primary concern is inward dependencies and domain-core protection rather than explicit port direction. | A pricing or approval rules library centers on aggregates, value objects, and domain services with few external integrations. |
| Onion | An existing project already separates domain, application, and infrastructure with inward dependencies. | Keep the Onion package structure and add JFoundry annotations and ArchUnit rules. |
| Onion Classical | The team deliberately uses distinct domain model, domain service, application service, and infrastructure rings. | A mature domain platform already has stable Onion Classical terminology and boundaries. |
| No full style yet | The scope is simple CRUD, a short-lived prototype, or has few business invariants and external ports. | An administration tool only maintains a small number of forms and lists. |

Quick decision:

```text
Need explicit external input/output direction -> Hexagonal
Need inward dependencies and domain-centered ring language -> Onion
Simple CRUD or short-lived prototype -> Do not introduce a full style yet
```

## Rule Entry Points

```java
@AnalyzeClasses(packages = "com.mycompany.myapp")
class ArchitectureTest {

    @ArchTest
    ArchTests rules = JFoundryRules.hexagonalStrict();
}
```

Use `JFoundryRules.hexagonalStrict()` for Hexagonal projects. Use `JFoundryRules.onionSimple()` or
`JFoundryRules.onionClassical()` for Onion projects. These entries include baseline guard rules and
the Hexagonal/Onion mutual-exclusion rule. Each primary-style entrypoint also fails when the
analyzed scope contains no matching architecture annotation at all. This prevents an annotation-
driven rule set from passing as an empty no-op. It does not require every possible role or ring to
appear in a deliberately partial analysis scope.

## References

- jMolecules Architecture: <https://github.com/xmolecules/jmolecules/tree/main/jmolecules-architecture>
- jMolecules ArchUnit: <https://github.com/xmolecules/jmolecules-integrations/tree/main/jmolecules-archunit>
- Hexagonal Architecture / Ports and Adapters: <https://alistair.cockburn.us/hexagonal-architecture/>
- Onion Architecture: <https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/>
