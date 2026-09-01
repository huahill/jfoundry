# Request Correlation

Request correlation gives each inbound HTTP request a short, validated identifier. It is useful for
correlating access logs, error responses, and application-owned audit records for one entry request. It
is not a distributed tracing protocol and does not replace OpenTelemetry `trace_id`, `span_id`, or W3C
`traceparent`.

## Selection And Defaults

Request correlation is an optional part of the Web capability. For applications that select JFoundry's
HTTP Web entry point, the runtime entry point enables request correlation by default; applications can
still disable it globally or exclude paths. Enabling request correlation does not enable HTTP access
logging. `HttpLoggingFilter` remains independently controlled and defaults to disabled.

The default semantics are:

- read the inbound `X-Request-Id`;
- accept only a bounded character set and length, generating a server-side value for missing, empty, or invalid input;
- place the final value in the runtime request context and, when response propagation is enabled, return `X-Request-Id`;
- clear the logging projection when the request ends;
- use the identifier only for correlation, never for authentication, authorization, idempotency, or access control.

The accepted identifier alphabet is exactly `[A-Za-z0-9._~-]`. The absolute maximum is 64 characters;
the generated UUID is 36 characters. `RequestCorrelationOptions.maximumLength` therefore accepts values
from 36 through 64 so a generated value always fits. Application code reads the active value through
`RequestCorrelationContext.current()`.

Applications may configure the header name, inbound acceptance, response propagation, maximum length,
and path exclusions, but must not relax validation to permit log-control characters or unbounded values.

## Runtime-Neutral Contract

The runtime-neutral module expresses only these semantics. It must not depend on Servlet, Spring, Quarkus,
Helidon, Logback, Log4j2, or business audit types:

- an immutable request-correlation identifier with validation and generation rules;
- configuration for the header, request context, and response propagation;
- a context contract that outer adapters can read;
- no logging-context projection SPI; runtime adapters use their own logging facilities.

The contract should live in the existing runtime-neutral `jfoundry-web` infrastructure module. HTTP
filters and container lifecycle APIs must remain outside `jfoundry-domain`, `jfoundry-application`, and
other core business modules.

## Runtime Adapters

Each runtime implements its inbound adapter and auto-configuration:

| Runtime | Adapter location | Responsibilities |
|---|---|---|
| Spring Boot / MVC | `jfoundry-webmvc-spring` and its Boot auto-configuration | Servlet filter, filter order, async/error dispatch, response header, and SLF4J logging projection |
| Quarkus REST | `jfoundry-web-quarkus-runtime` | Quarkus REST request context, response header, and the runtime logging-context projection |
| Helidon MP | `jfoundry-web-helidon` | JAX-RS/Helidon request context and response header; `System.Logger` has no MDC-equivalent projection |

All runtimes must have the same observable semantics for validation, generation, headers, context scope,
async cleanup, and path exclusions. Spring re-establishes state on `ASYNC` and `ERROR` redispatches but does
not copy MDC into arbitrary worker threads. Jakarta REST adapters restore request-thread state in the response
filter; propagation into application-managed worker threads is not implied. Each runtime needs its own wiring
and async/error-dispatch tests.

## Configuration

Spring Boot binds `enabled`, `header-name`, `accept-incoming`, `write-response`, `maximum-length`, and
`excluded-paths` below `jfoundry.web.mvc.request-correlation`. Spring exclusions use application paths and
Ant-style patterns; the Servlet context path is removed before matching. Quarkus and Helidon use the prefixes
`jfoundry.web.quarkus.request-correlation` and `jfoundry.web.helidon.request-correlation`; their comma-separated
`excluded-paths` values use the same Ant-style `*`, `**`, and `?` patterns. All runtimes default to `X-Request-Id`,
accept incoming values, write the response header, use maximum length 64, and have no exclusions.

The Spring MVC adapter must establish the context before HTTP diagnostic logging. The recommended order is:

```text
Request correlation     HIGHEST_PRECEDENCE + 10
HttpLoggingFilter       HIGHEST_PRECEDENCE + 20
Spring Security          later runtime registration
Application audit        security chain or application boundary
```

Auto-configuration must register this order explicitly; it must not depend on incidental application
`@Order` behavior. Other runtimes must provide the same ordering relative to their inbound diagnostic logger.

## Logging Context

The request context is the authoritative source of the identifier; a logging context is only a projection.
Runtime adapters may project `request_id` through the logging facade guaranteed by their runtime. The
runtime-neutral core must not depend on a concrete logging implementation.

The Spring adapter uses `org.slf4j.MDC`, without depending directly on Logback or Log4j2:

```text
application code -> SLF4J MDC -> Logback
application code -> SLF4J MDC -> Log4j2
```

With the Log4j2 SLF4J binding, application code does not need to use `ThreadContext`. Quarkus uses SLF4J MDC.
Helidon MP currently uses `System.Logger`, which has no standard MDC or thread-context API; Helidon therefore
does not claim automatic `request_id` fields in arbitrary application log records. Applications can read
`RequestCorrelationContext.current()` and include the value in structured diagnostics explicitly. Every adapter
clears its projection or request context at the terminal response callback; thread switches must use the runtime's
context-propagation mechanism rather than assuming MDC propagation.

Logs may contain all three fields:

```json
{
  "request_id": "request_1234",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7"
}
```

`request_id` comes from request correlation. `trace_id` and `span_id` come from the host application's
OpenTelemetry/Micrometer tracing configuration. JFoundry must not rename one into the other or write one
protocol into the other.

## OpenTelemetry Boundary

Keep the meanings separate:

```text
X-Request-Id / request_id = application correlation number for one HTTP entry request
trace_id                  = distributed-operation trace identifier
span_id                   = current service or operation span identifier
traceparent               = W3C trace-context propagation protocol
```

Request correlation must not put `trace_id` into `X-Request-Id`, put `X-Request-Id` into `traceparent` or
OpenTelemetry baggage, or write the request identifier into an application audit table automatically.
OpenTelemetry instrumentation owns outbound trace propagation. Outbound `X-Request-Id`, if a business
contract needs it, is a separate client capability.

## Boundaries With HTTP Logging And Business Audit

- HTTP diagnostic logging owns method, redacted URI, status, duration, and level-selected header/body diagnostics. It defaults to disabled and does not replace tracing or audit.
- Request correlation provides only generic request context and optional response propagation. It must not understand API keys, actors, operations, audit tables, or business error codes.
- Applications continue to own audit events and redaction. An audit event may store `request_id` and optionally `trace_id`, but must not turn the long-lived audit record into a raw URL, authorization, or request-body log.

## Security And Compatibility

`X-Request-Id` is untrusted input. Implementations must bound its length and character set, reject
newlines, control characters, and oversized values, and use structured logging parameters rather than
concatenating an unchecked header. The identifier is not a secret and must not grant cross-request access.

Enabling the capability by default adds a response header and logging-context field, so it is an observable
compatibility change. Provide a global disable switch and path exclusions. When upgrading an existing
application, check for an existing filter before enabling the shared one. After migration, remove the
local filter and let business audit code read the shared context.

## Verification Matrix

Every runtime must verify at least:

1. missing, valid, invalid, oversized, and control-character input headers;
2. consistency between the generated value, request context, and response header;
3. diagnostic logs seeing `request_id` after request correlation runs (Spring and Quarkus; Helidon applications
   must add the value explicitly because `System.Logger` has no MDC API);
4. correlation for 401, 403, 404, and unmatched-path requests;
5. cleanup after synchronous, asynchronous, timeout, and error dispatch completion;
6. independent semantics for `request_id`, `trace_id`, and `span_id` when OpenTelemetry is enabled;
7. equivalent output through Spring's Logback and Log4j2 bindings without a core dependency on either backend.

## Implementation Sequence

1. Define the runtime-neutral contract and defaults in `jfoundry-web`.
2. Implement and test inbound adapters and auto-configuration for Spring, Quarkus, and Helidon.
3. Fix request-correlation registration before each runtime's HTTP diagnostic logger.
4. Update the capability catalog, runtime guides, configuration metadata, and adoption-readiness scope.
5. Migrate `rdc-openapi` and `rdc-openapi-ops` to the shared entry point and remove their duplicate filters.
6. Verify audit, response headers, diagnostic logs, and tracing correlation before publishing compatibility notes.

Default enablement is a production-support claim only after all three runtime semantics and test suites are complete.
