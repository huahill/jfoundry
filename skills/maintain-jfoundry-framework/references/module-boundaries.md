# Module Boundaries

## Core Principle

jfoundry's core modules must stay independent of concrete application runtimes. Spring, Spring Boot, Helidon, Quarkus, CDI, Jakarta runtime integration, scheduling, transaction synchronization, property binding, and auto-configuration belong outside the core.

Stable, low-intrusion libraries such as jMolecules and `slf4j-api` may appear in core modules when they express framework contracts or architecture semantics.

## Module Roles

### Framework-Neutral Core

- `jfoundry-domain`: domain building blocks, entity/value object/event/repository abstractions.
- `jfoundry-architecture`: architecture style facade modules and aggregation.
- `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test`: reusable ArchUnit and test helpers for framework users.
- `jfoundry-application`: application-layer contracts, CQRS annotations, event dispatch contracts, messaging SPI, Outbox/Inbox core contracts.
- `jfoundry-infrastructure`: framework-neutral technical adapters for persistence, messaging, payload serialization, JobRunr dispatching, and similar technologies.
- `jfoundry-core/jfoundry-starters`: runtime-neutral dependency-composition modules. Domain and
  Application starters are direct children; capability-named starters for infrastructure adapters live
  directly under its `infrastructure` directory.

### Runtime Integration

- `jfoundry-runtime-integrations/jfoundry-spring/runtime`: Spring Framework adapters such as local event publishing, transaction runner, messaging sender wrappers, outbox transaction/scheduling integration, and Web MVC ProblemDetail support.
- `jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/*`: capability-specific Spring Boot auto-configuration, conditions, properties, and runtime wiring.
- `jfoundry-runtime-integrations/jfoundry-spring/starters`: dependency entry points only.
- `jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests`: Spring middleware, Testcontainers, and runtime integration verification.
- `jfoundry-runtime-integrations/jfoundry-quarkus/runtime`: Quarkus runtime extension artifacts and CDI behavior.
- `jfoundry-runtime-integrations/jfoundry-quarkus/deployment`: Quarkus build-time processors and Native Image registration.
- `jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests`: Quarkus consumer and Native Image integration verification.
- `jfoundry-runtime-integrations/jfoundry-helidon/runtime`: Helidon MP CDI, JTA, JAX-RS, scheduling, and JPA runtime behavior.
- `jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests`: Helidon MP consumer and Native Image integration verification.

Runtime-specific middleware, Testcontainers, database/broker compatibility, and profile-driven checks belong
in the affected runtime's direct `jfoundry-<runtime>-integration-tests` module. Framework-neutral tests belong
beside the core or infrastructure implementation they verify.

## Dependency Direction

Allowed direction:

```text
domain / architecture
  <- application
  <- infrastructure adapters
  <- spring runtime adapters
  <- boot auto-configuration
  <- starters
```

Practical rules:

- Domain must not depend on Spring, MyBatis, JPA, broker clients, Jackson object mapping details, or runtime integration.
- Application modules define contracts and framework-neutral services. They may depend on domain abstractions and jMolecules semantics.
- Infrastructure modules implement or consume application/domain contracts without registering Spring Boot auto-configuration.
- Spring runtime modules may depend on application contracts and framework-neutral adapters.
- Helidon runtime modules may depend on application contracts and framework-neutral adapters, but must not
  introduce a Quarkus deployment layer or Spring Boot starter semantics.
- Boot auto-configuration wires beans, conditions, properties, and integration defaults.
- Starters depend on modules; they do not contain Java runtime behavior.

## Internal Architecture Style

jfoundry framework internals use Onion simplified:

- `org.jfoundry.domain..` is the domain ring.
- `org.jfoundry.application..` is the application ring.
- `org.jfoundry.infrastructure..` is the infrastructure ring.

Use jMolecules architecture annotations internally. The JFoundry wrapper annotations remain public facades for business projects.

## Red Flags

- A core module starts depending on `spring-*`, `spring-boot-*`, servlet APIs, scheduling APIs, CDI, Jakarta runtime APIs, or runtime lifecycle APIs.
- An adapter module adds `AutoConfiguration.imports`.
- A starter module gains Java source with runtime behavior.
- A default starter starts pulling broker clients, Outbox store adapters, Inbox store adapters, JobRunr, or MyBatis-Plus stores implicitly.
- Outbox/Inbox persistence data starts extending aggregate persistence abstractions.
- Boot auto-configuration is marked as an Onion ring package.
