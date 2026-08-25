# Web

`jfoundry-web` is JFoundry's runtime-neutral Web capability foundation. It owns shared HTTP problem
semantics, while runtime adapters render those semantics through their respective HTTP stacks. The
currently published Web capabilities are RFC 9457 Problem Details for HTTP APIs and opt-in outbound
HTTP client support for Spring applications.

## Select A Web Capability

| Need | Spring Boot | Quarkus | Helidon MP |
|---|---|---|---|
| RFC 9457 Problem Details for an HTTP API | `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` | `jfoundry-web-helidon-runtime` |
| Outbound HTTP client support | `jfoundry-web-spring-boot-starter` or `jfoundry-web-spring` | Not provided | Not provided |

`jfoundry-web-spring` requires an application-provided Spring Web API. A Spring Boot application
can add `jfoundry-web-spring-boot-starter`, which supplies the Spring Boot RestClient integration and
the `jfoundry.web.rest-client.logging-level` property. `Not provided` means that JFoundry does not currently
publish an adapter for that runtime; it is not an implicit support claim.

## Problem Details (RFC 9457)

Use this capability when an HTTP API needs stable RFC 9457 `application/problem+json` responses
for JFoundry business failures. It translates a supported application or domain exception into an
HTTP response at the runtime boundary; domain and application code do not select HTTP status codes.

### Add The Runtime Entry Point

| Runtime | Consumer dependency | HTTP integration |
|---|---|---|
| Spring Boot | `jfoundry-webmvc-spring-boot-starter` | Spring MVC |
| Quarkus | `jfoundry-web-quarkus-runtime` | Quarkus REST with Jackson |
| Helidon MP | `jfoundry-web-helidon-runtime` | JAX-RS |

The entry points include the runtime-neutral `jfoundry-web` module. Applications
normally add only the entry point shown above. Import the core and matching runtime BOMs first as
described in [Getting Started](../integration/getting-started.md).

### Shared Contract

Supported responses contain RFC 9457 `type`, `title`, `status`, and `detail` members. The `type` URI
is the stable machine-readable identifier for the problem. Custom extensions preserve JSON scalar,
array, and object types. They cannot replace RFC 9457 reserved members and should be defined only
when they add semantics for a specific problem type.

The built-in catalog maps these JFoundry exceptions: `InvalidArgumentException`,
`NotFoundException`, `ConflictException`, `ExternalAccessException`,
`DomainRuleViolationException`, and `DomainStateException`. It also owns the shared HTTP statuses
`400`, `404`, `405`, `406`, `413`, `415`, and `503` when the runtime reports them.

The messages of `InvalidArgumentException`, `NotFoundException`, `ConflictException`,
`DomainRuleViolationException`, and `DomainStateException` become caller-facing `detail` values. Keep
those messages in business language and do not include credentials, internal endpoints, SQL, or other
diagnostic data.

`ExternalAccessException` is different: its diagnostic message is masked by default. A concrete
translated exception may use the protected constructor with a reviewed public detail when it owns a
stable, actionable explanation:

```java
final class MksAuthenticationException extends ExternalAccessException {

    MksAuthenticationException(Throwable cause) {
        super(
                "MKS deployment JWT signing failed",
                cause,
                "Deployment authorization is temporarily unavailable."
        );
    }
}
```

The catalog uses that explicit detail for the `urn:jfoundry:problem:external-access` response. It
never derives a public detail from the diagnostic message, the cause, or `cause.getMessage()`.
Existing constructors remain masked and continue to produce
`The requested operation is temporarily unavailable.`

Applications can provide a `ProblemMapper` to map an owned exception to a `ProblemDescriptor`.
Use this for stable, application-specific errors, rather than leaking implementation exceptions or
forcing an HTTP concern into the domain model.

### Request Validation Problems

Spring MVC, Quarkus REST, and Helidon MP use the dedicated
`urn:jfoundry:problem:request-validation` type for supported request-input validation failures. Its
`errors` extension follows the RFC 9457 validation-error example. Every entry has a caller-facing
`detail`; an error with a reliable location in the JSON request document also has a `pointer` encoded
as a JSON Pointer URI fragment:

```json
{
  "type": "urn:jfoundry:problem:request-validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request failed validation. See 'errors' for details.",
  "errors": [
    {
      "detail": "must not be empty",
      "pointer": "#/services"
    }
  ]
}
```

Pointer tokens escape `~` and `/` according to RFC 6901. The URI fragment representation also
percent-encodes other characters as required by RFC 3986.

Pointers describe locations in the JSON request document, not Java property paths in general. Field
and container-element failures proven to originate from a JSON body can therefore use pointers such
as `#/services/0`. Query, path, header, cookie, matrix, form, model-attribute, and multipart request
parameters contain only `detail`, even when validation reports a nested Java property path. Object-level
and cross-parameter constraints also contain only `detail` because they do not identify one JSON value.

Malformed JSON, message-conversion failures, and other failures that occur before validation retain the
runtime's HTTP bad-request problem type; they are not reported as request-validation problems. Rejected
values are never included because request fields may contain credentials, tokens, or large payloads.
The runtime adapters deliberately exclude return-value and internal service validation failures from
this client-error contract. Spring MVC, Quarkus REST, and Helidon MP apply these provenance rules to the
request-source metadata exposed by their own HTTP stacks while producing the same public response shape.

Spring MVC obtains validation through its normal Web MVC integration. A Quarkus application must add
`quarkus-hibernate-validator`; JFoundry registers the mapper only when that capability is present. A
Helidon MP application must add `helidon-microprofile-bean-validation`. Applications also remain
responsible for selecting the JSON provider used to deserialize request bodies.

### Deliberate Boundaries

- Unknown exceptions and HTTP failures outside the supported status set retain the runtime's normal
  handling. This capability is not an application's universal exception policy.
- Authentication and authorization remain owned by the selected security integration. A security
  adapter can render its own `401` or `403` descriptor through the runtime renderer.
- The published adapters currently cover Spring MVC, Quarkus REST, and Helidon MP JAX-RS. They do
  not claim support for other HTTP stacks.

### Runtime Reference

- [Spring Boot Runtime Assembly](../implementations/spring-boot.md) covers auto-configuration and
  Spring MVC replacement rules.
- [Quarkus Runtime Integration](../implementations/quarkus.md) covers extension composition and
  Quarkus REST behavior.
- [Helidon MP Runtime Integration](../implementations/helidon.md) covers CDI and JAX-RS behavior.

## Spring HTTP Integration And Diagnostic Logging

`jfoundry-web-spring` provides opt-in outbound `RestClient` support. Configure only the builder owned
by the integration with `RestClientSupport.configure(builder)`, then execute that call through
`RestClientSupport.execute(...)`. A non-success response becomes an `HttpResponseException` containing
only its status code. Transport and response-decoding failures become an `HttpRequestException` with a
safe failure kind while retaining the original exception as its cause for server-side diagnostics.

The APIs are organized by abstraction level. Import `HttpLoggingLevel` and `HttpLoggingSupport` from
`org.jfoundry.http.spring`, `HttpLoggingInterceptor` from `org.jfoundry.http.spring.client`, and the
`RestClient` facade and translated exceptions from `org.jfoundry.web.spring.client`. These replace the
old `org.jfoundry.web.spring` locations; no compatibility aliases are provided. `ProblemDetailRenderer`
remains in `org.jfoundry.web.spring`.

Outbound logging defaults to `BASIC`. Applications can select all four levels through
`RestClientSupport.configure(builder, HttpLoggingLevel)`. Spring Boot-managed builders use
`jfoundry.web.rest-client.logging-level`, also defaulting to `BASIC`. The client `durationMs` starts
immediately before `ClientHttpRequestExecution.execute(...)` and ends when response headers are
available or execution fails. It excludes response-body consumption and decoding and is not
end-to-end latency.

The Web MVC starter also provides inbound Servlet logging through `HttpLoggingFilter`. It is disabled
by default with `jfoundry.web.mvc.logging-level=NONE`; set `BASIC`, `HEADERS`, or `FULL` to enable it.
Inbound `durationMs` runs from initial Filter entry to synchronous completion or terminal async
complete, error, or timeout. It does not claim that the client has received all response bytes.

Both directions emit only at `DEBUG`. `BASIC` records query-free method/URI, status or failure, and
`durationMs` without body wrappers. `HEADERS` adds headers after case-insensitive redaction of
authorization, credentials, cookies, tokens, secrets, and API keys. `FULL` adds JSON bodies after
nested-field redaction and retains at most 8 KiB; non-JSON, malformed, incomplete, and oversized bodies
are described rather than exposed. The client may read an unconsumed error response on close for this
diagnostic purpose. Servlet capture forwards bytes immediately and does not delay streaming output.

These diagnostic access logs do not replace Micrometer metrics or traces, and they do not publish or
stand in for application-owned business audit events. The
[Spring Boot Runtime Assembly](../implementations/spring-boot.md) documents composition and replacement
rules in more detail.
