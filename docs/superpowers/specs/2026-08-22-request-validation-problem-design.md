# Cross-Runtime Request Validation Problem Design

## Status

Approved in discussion on 2026-08-22. This design refines the request-validation work already present on
`codex/webmvc-validation-problem` and defines the remaining implementation and verification scope.

## Context

JFoundry exposes RFC 9457 problem details through the runtime-neutral `jfoundry-web` infrastructure adapter
and runtime integrations for Spring MVC, Quarkus REST, and Helidon MP. The current branch introduces a
dedicated request-validation problem type:

```text
urn:jfoundry:problem:request-validation
```

It also removes the non-standard top-level `code` member and represents individual constraint failures in an
`errors` extension. Initial runtime support maps request-body Bean Validation failures, but the behavior and test
coverage must cover the complete HTTP request-validation surface consistently.

The existing Quarkus and Helidon adapters duplicate the same Jakarta Validation path conversion. Treating the
Jakarta Validation API itself as a runtime framework dependency would be inaccurate: it is a portable Jakarta
specification API, although it is not part of Java SE and still requires a provider and runtime integration to
perform validation.

## Goals

- Return a precise RFC 9457 request-validation problem for caller-correctable Jakarta Bean Validation failures.
- Cover body, query, path, header, cookie, matrix, request-part, model/bean, container-element, class-level, and
  cross-parameter validation where the runtime exposes those concepts.
- Keep the public response contract equal across Spring MVC, Quarkus, and Helidon.
- Generate JSON Pointers only for locations that are proven to belong to the JSON request document.
- Share portable Jakarta `ConstraintViolation` conversion between Quarkus and Helidon.
- Preserve internal failures and response/return-value validation as server-side failures rather than reporting
  them as caller-correctable `400` responses.
- Fix clean-reactor Quarkus build ordering so the full Java 25 CI build succeeds without artifacts preinstalled in
  the local Maven repository.

## Non-Goals

- Defining a general-purpose JFoundry validation abstraction or validation orchestration API.
- Adding `jfoundry-validation-core`, `jfoundry-validation-jakarta`, or
  `jfoundry-web-jakarta-validation` modules.
- Providing or configuring a Jakarta Validation implementation such as Hibernate Validator.
- Treating malformed JSON, type conversion, missing required transport parameters, or unreadable request bodies
  as Bean Validation failures.
- Adding `location`, `parameter`, rejected values, constraint names, or runtime-specific metadata to `errors`.
- Generating JSON Pointers for query parameters, path variables, headers, cookies, matrix variables, request
  parts, form/model/bean parameters, class-level constraints, or cross-parameter constraints.

## Response Contract

A Bean Validation request failure uses the following shape:

```json
{
  "type": "urn:jfoundry:problem:request-validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request failed validation. See 'errors' for details.",
  "instance": "/example",
  "errors": [
    {
      "detail": "must not be empty",
      "pointer": "#/services"
    }
  ]
}
```

Each `errors` entry always has `detail`. It has `pointer` only when the failure can be located within the JSON
request document. The pointer uses RFC 6901 fragment representation and escapes `~` as `~0` and `/` as `~1`.
Rejected values are never included.

Errors are sorted by logical document path and then detail so responses and tests remain deterministic. A null
validation message falls back to `is invalid`.

## Error Classification

The dedicated request-validation problem is limited to constraint violations caused by the inbound HTTP request.
The following failures remain the ordinary `http-bad-request` problem because they are transport parsing or
binding failures rather than Jakarta Bean Validation constraint failures:

- malformed JSON;
- JSON value/type mismatches;
- missing required transport parameters detected before Bean Validation;
- request parameter conversion failures;
- unreadable or unsupported request content.

Return-value constraint violations, response validation failures, and validation failures from internal service
objects must not be translated to a client `400`. They continue through the runtime's server-error handling.
When one runtime exception contains a return-value violation, the adapter must not partially convert the remaining
violations into a `400`; the exception remains a server-side failure.

## Architecture And Module Placement

### Runtime-Neutral Web Infrastructure

The existing `jfoundry-core/jfoundry-infrastructure/jfoundry-web` module remains the owner of:

- the `RequestValidationProblem` response contract;
- JSON Pointer rendering;
- portable conversion from Jakarta `ConstraintViolation` instances to
  `RequestValidationProblem.Error` instances;
- deterministic ordering and message fallback.

No new module is introduced. The shared converter is Web-specific because its output is an RFC 9457 request
problem representation; it is not a general validation facility.

`jfoundry-web` declares `jakarta.validation-api` as an optional compile dependency because the shared public
converter references `ConstraintViolation`. Runtime integrations that use the converter must continue to declare
their own Validation API or validation integration dependency. This does not permit a validation provider, CDI,
Jakarta REST, container lifecycle API, or runtime interception mechanism to enter the module. The Jakarta
Validation coordinate and version belong in the runtime-neutral Foundation dependency management.

The shared public utility, named `JakartaRequestValidationErrors`, accepts the violations and a predicate that
classifies whether each violation originated from the JSON request document. It returns
`RequestValidationProblem.Error` instances. It extracts a document path only when the predicate returns true. A
false or uncertain classification produces a detail-only error. The utility does not classify exceptions or
inspect Jakarta REST annotations.

### Runtime Adapters

Runtime adapters retain responsibility for information that is not portable across the supported stacks:

| Runtime | Runtime-specific responsibility | Shared responsibility |
| --- | --- | --- |
| Spring MVC | Handle `MethodArgumentNotValidException` and `HandlerMethodValidationException`; use Spring parameter-source metadata and `MessageSource`; exclude return-value validation | RFC 9457 contract and JSON Pointer rendering |
| Quarkus REST | Handle `ResteasyReactiveViolationException`; distinguish return values and identify the JSON entity parameter from REST resource metadata | Jakarta violation message/path conversion and ordering |
| Helidon MP | Handle `ConstraintViolationException`; prove that the root is a JAX-RS resource, exclude return values, and identify the JSON entity parameter | Jakarta violation message/path conversion and ordering |

Runtime-specific exception types, Spring validation types, Jakarta REST annotations, CDI APIs, and Quarkus or
Helidon APIs remain outside `jfoundry-web`.

## JSON Document Provenance

A Jakarta property path alone does not prove that a property belongs to the JSON request document. Cascaded
validation of a query bean, for example, can contain property nodes that look identical to JSON object fields.
Therefore pointer generation follows these rules:

1. The runtime adapter first classifies the request parameter source.
2. A parameter proven to be the JSON entity/body parameter is eligible for a pointer.
3. The shared converter renders property names and iterable indexes or keys after that parameter as JSON Pointer
   tokens.
4. Standard REST-bound query, path, header, cookie, matrix, form, bean, and context parameters are not eligible.
5. Unknown or runtime-specific parameter bindings are not eligible unless the adapter can prove that they denote
   the request entity.
6. Class-level, root-body, and cross-parameter failures have no field path and therefore receive only `detail`.

This is deliberately conservative: omitting a pointer is valid, while emitting a pointer that addresses the
wrong document is misleading.

## Spring MVC Coverage

`MethodArgumentNotValidException` continues to cover request-body binding validation. Spring 6.1+
`HandlerMethodValidationException` is added for method validation, including:

- `@RequestParam`;
- `@PathVariable`;
- request headers and cookies;
- matrix variables;
- request parts and model attributes;
- request-body container-element constraints;
- cross-parameter constraints.

The handler uses Spring's visitor/result model to determine the parameter source instead of deriving it from a
field-name string. Only request-body result paths can produce pointers. Return-value validation is rethrown or
otherwise delegated to server-error handling.

## Quarkus And Helidon Coverage

Both Jakarta REST adapters use the shared Jakarta conversion but keep their distinct exception classification:

- Quarkus accepts only its REST validation exception and rejects any exception containing a return-value node.
- Helidon accepts only constraint violations whose root bean is a JAX-RS resource and rejects return-value nodes;
  unrelated `ConstraintViolationException` instances remain internal failures.

Each adapter identifies the executable parameter index from Jakarta Validation path nodes and resolves its REST
binding. A standard unbound entity parameter is eligible for a JSON Pointer. Explicit query, path, header,
cookie, matrix, form, bean, and context bindings are not. If executable or binding metadata cannot be resolved,
the error remains detail-only.

## Documentation Boundary Clarification

Framework documentation and the repository maintenance skill must distinguish portable Jakarta specification
APIs from runtime integration mechanisms. Domain and application modules remain independent of Jakarta Validation.
Infrastructure adapters may selectively use stable Jakarta specification APIs when those APIs directly express
the adapter contract, as the existing Jakarta Persistence adapters already do.

This does not make all Jakarta APIs valid in runtime-neutral modules. CDI lifecycle, Jakarta REST dispatch,
transaction interception, container bootstrapping, and provider-specific behavior remain runtime integration
concerns.

## Test Strategy

### Runtime-Neutral Tests

`jfoundry-web` tests cover:

- property and nested-property paths;
- list indexes and map keys;
- container-element paths;
- JSON Pointer escaping;
- detail-only errors when document provenance is false or unknown;
- class-level and cross-parameter paths;
- deterministic ordering;
- null-message fallback;
- absence of rejected values and unapproved extension members.

### Runtime Adapter Tests

Spring MVC, Quarkus, and Helidon each receive focused tests for the request sources they support:

- JSON body field and nested/container-element constraints with pointers;
- query, path, header, cookie, matrix, part, and bean/model constraints without pointers where supported;
- class-level and cross-parameter constraints without pointers;
- return-value validation excluded from `400` handling;
- internal validation excluded from `400` handling;
- malformed or conversion failures remaining `http-bad-request`.

Integration tests exercise actual runtime wiring rather than only constructing exception objects. Quarkus and
Helidon Native Image request-validation probes remain part of the compatibility gate.

### Build Verification

Verification proceeds from focused module tests to runtime integration tests. The Quarkus reactor issue must be
reproduced and fixed with a clean temporary Maven repository so success cannot depend on locally installed
`1.3.0-SNAPSHOT` deployment artifacts. Because the change affects POMs, shared infrastructure, runtime adapters,
and Native Image behavior, `scripts/verify-ci-matrix.sh` is required before the final push when Java 25 is
available. The GitHub Merge gate remains authoritative.

## Compatibility Impact

- The response contract intentionally removes the previous non-standard top-level `code` field.
- Bean Validation failures gain the `errors` extension and the dedicated problem type.
- Existing clients that only consume RFC 9457 standard members remain compatible.
- Clients that depended on `code` must migrate to `type` and HTTP `status`.
- The Jakarta Validation API becomes an optional compile-time dependency of `jfoundry-web`; no provider or
  runtime is selected or configured, and runtime integrations retain their explicit validation dependencies.
- No new starter, module, configuration property, or automatic validation behavior is introduced.

## Acceptance Criteria

- Spring MVC, Quarkus, and Helidon produce the same request-validation problem contract.
- Every caller-correctable Bean Validation request failure supported by a runtime is covered by focused tests.
- JSON Pointers appear only for proven JSON document locations.
- Return-value and internal validation failures are never exposed as client `400` responses.
- Parsing and conversion failures remain `http-bad-request`.
- Quarkus and Helidon share portable Jakarta violation conversion without moving runtime exception handling into
  `jfoundry-web`.
- No general validation module or validation provider is introduced.
- A full Java 25 build succeeds from a clean Maven repository, and the GitHub Merge gate passes.
