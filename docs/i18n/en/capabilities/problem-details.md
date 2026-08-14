# REST Problem Details

Use this capability when an HTTP API needs stable RFC 9457 `application/problem+json` responses
for JFoundry business failures. It translates a supported application or domain exception into an
HTTP response at the runtime boundary; domain and application code do not select HTTP status codes.

## Add The Runtime Entry Point

| Runtime | Consumer dependency | HTTP integration |
|---|---|---|
| Spring Boot | `jfoundry-webmvc-spring-boot-starter` | Spring MVC |
| Quarkus | `jfoundry-web-quarkus-runtime` | Quarkus REST with Jackson |
| Helidon MP | `jfoundry-web-helidon-runtime` | JAX-RS |

The entry points include the runtime-neutral `jfoundry-web` module. Applications
normally add only the entry point shown above. Import the core and matching runtime BOMs first as
described in [Getting Started](../integration/getting-started.md).

## Shared Contract

Supported responses contain RFC 9457 `type`, `title`, `status`, and `detail` members, plus the
stable JFoundry `code` extension. Custom extensions preserve JSON scalar, array, and object types.
They cannot replace RFC 9457 reserved members.

The built-in catalog maps these JFoundry exceptions: `InvalidArgumentException`,
`NotFoundException`, `ConflictException`, `ExternalAccessException`,
`DomainRuleViolationException`, and `DomainStateException`. It also owns the shared HTTP statuses
`400`, `404`, `405`, `406`, `413`, `415`, and `503` when the runtime reports them.

Applications can provide a `ProblemMapper` to map an owned exception to a `ProblemDescriptor`.
Use this for stable, application-specific errors, rather than leaking implementation exceptions or
forcing an HTTP concern into the domain model.

## Deliberate Boundaries

- Unknown exceptions and HTTP failures outside the supported status set retain the runtime's normal
  handling. This capability is not an application's universal exception policy.
- Authentication and authorization remain owned by the selected security integration. A security
  adapter can render its own `401` or `403` descriptor through the runtime renderer.
- The published adapters currently cover Spring MVC, Quarkus REST, and Helidon MP JAX-RS. They do
  not claim support for other HTTP stacks.

## Runtime Reference

- [Spring Boot Runtime Assembly](../implementations/spring-boot.md) covers auto-configuration and
  Spring MVC replacement rules.
- [Quarkus Runtime Integration](../implementations/quarkus.md) covers extension composition and
  Quarkus REST behavior.
- [Helidon MP Runtime Integration](../implementations/helidon.md) covers CDI and JAX-RS behavior.
