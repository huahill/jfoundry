# Cross-Runtime Request Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete RFC 9457 request-validation handling across Spring MVC, Quarkus, and Helidon while sharing portable Jakarta Validation conversion and preserving accurate JSON Pointer provenance.

**Architecture:** `jfoundry-web` owns the response contract and a Jakarta-specification-based violation converter. Quarkus and Helidon keep runtime exception classification and JAX-RS parameter-source detection; Spring uses its own validation result visitor and localization model. Only proven JSON request-document paths receive pointers, while non-body, cross-parameter, return-value, and internal validation retain their defined behavior.

**Tech Stack:** Java 25, Maven, Jakarta Validation 3.1, Spring Framework 7, Quarkus REST, Helidon MP, JUnit Jupiter, AssertJ, MockMvc, RFC 9457, RFC 6901.

---

### Task 1: Add The Portable Jakarta Validation Converter

**Files:**
- Modify: `jfoundry-boms/jfoundry-foundation-dependencies/pom.xml`
- Modify: `jfoundry-core/jfoundry-infrastructure/jfoundry-web/pom.xml`
- Create: `jfoundry-core/jfoundry-infrastructure/jfoundry-web/src/main/java/org/jfoundry/problem/JakartaRequestValidationErrors.java`
- Create: `jfoundry-core/jfoundry-infrastructure/jfoundry-web/src/test/java/org/jfoundry/problem/JakartaRequestValidationErrorsTest.java`
- Modify: `scripts/VerifyDependencyBoundaries.java`
- Test: `scripts/tests/verify-dependency-boundaries-test.sh`

- [x] **Step 1: Write converter tests with synthetic Jakarta paths**

Create tests that construct deterministic `ConstraintViolation` and `Path.Node` fakes and assert this public API:

```java
List<RequestValidationProblem.Error> errors = JakartaRequestValidationErrors.from(
        List.of(bodyViolation, queryViolation), violation -> violation == bodyViolation);

assertThat(errors).containsExactly(
        RequestValidationProblem.Error.atPath(List.of("services", "0", "image/url"), "must be valid"),
        RequestValidationProblem.Error.forRequest("must not be empty"));
```

Cover nested properties, list indexes, map keys, JSON Pointer escaping through `RequestValidationProblem.create`,
class-level and cross-parameter detail-only errors, false provenance, deterministic sorting, and null-message
fallback.

- [x] **Step 2: Run the focused test and verify it fails**

Run:

```bash
mvn -pl jfoundry-core/jfoundry-infrastructure/jfoundry-web -am \
  -Dtest=JakartaRequestValidationErrorsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `JakartaRequestValidationErrors` does not exist.

- [x] **Step 3: Manage and declare the specification API**

Add `jakarta-validation.version` set to `3.1.1` and manage `jakarta.validation:jakarta.validation-api` in the
Foundation BOM. Add it to `jfoundry-web` with `<optional>true</optional>`. Update the dependency-boundary checker
and its fixture tests so portable Jakarta specification APIs are allowed in infrastructure adapters while Jakarta
runtime/container APIs remain prohibited from Domain and Application modules.

- [x] **Step 4: Implement the converter**

Create this public contract:

```java
public final class JakartaRequestValidationErrors {
    public static List<RequestValidationProblem.Error> from(
            Iterable<? extends ConstraintViolation<?>> violations,
            Predicate<? super ConstraintViolation<?>> requestDocumentViolation) {
        // Iterate once, render only PROPERTY and iterable locations after a PARAMETER node,
        // produce detail-only errors when provenance is false or no document token exists,
        // then sort by path and detail.
    }
}
```

Use Java 23 Markdown documentation comments. Do not import Jakarta REST, CDI, a validation provider, or a runtime
exception. The converter must not inspect rejected values.

- [x] **Step 5: Run focused tests and boundary verification**

Run:

```bash
mvn -pl jfoundry-core/jfoundry-infrastructure/jfoundry-web -am test
bash scripts/verify-dependency-boundaries-test.sh
bash scripts/verify-dependency-boundaries.sh
```

Expected: all commands pass.

- [x] **Step 6: Commit the shared converter**

```bash
git add jfoundry-boms/jfoundry-foundation-dependencies/pom.xml \
  jfoundry-core/jfoundry-infrastructure/jfoundry-web \
  scripts/VerifyDependencyBoundaries.java scripts/tests/verify-dependency-boundaries-test.sh
git commit -m "feat(web): share Jakarta validation error conversion"
```

### Task 2: Refactor And Complete Quarkus Request Validation

**Files:**
- Modify: `jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime/pom.xml`
- Modify: `jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime/src/main/java/org/jfoundry/web/quarkus/ProblemDetailsExceptionMappers.java`
- Modify: `jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime/src/test/java/org/jfoundry/web/quarkus/ProblemDetailsExceptionMapperTest.java`

- [x] **Step 1: Add failing executable-validation coverage**

Extend the test resource with body, query, path, header, cookie, matrix, bean, class-level, cross-parameter,
container-element, and return-value cases. Use real Jakarta executable validation to obtain violations. Assert:

```java
assertThat(errorForBody).containsEntry("pointer", "#/services/0");
assertThat(errorForQuery).containsOnlyKeys("detail");
assertThat(errorForCrossParameter).containsOnlyKeys("detail");
```

Also assert that any exception containing a return-value violation is rethrown unchanged.

- [x] **Step 2: Run the focused Quarkus runtime test and verify failure**

Run:

```bash
mvn -pl jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime -am \
  -Dtest=ProblemDetailsExceptionMapperTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-body cascaded properties incorrectly receive pointers or the new expectations fail.

- [x] **Step 3: Implement conservative Quarkus parameter provenance**

Replace local Jakarta path conversion with `JakartaRequestValidationErrors.from`. Keep
`ResteasyReactiveViolationException` classification local. Resolve the method and `ParameterNode` index from the
constraint path, then classify standard JAX-RS bound parameters and Quarkus REST parameter annotations as
non-document sources. Treat an ordinary unbound entity parameter containing only neutral annotations such as
`@Valid` as the JSON document. Return false whenever method or binding metadata cannot be resolved.

- [x] **Step 4: Run the Quarkus runtime tests**

Run the command from Step 2 and then:

```bash
mvn -pl jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime -am test
```

Expected: all tests pass.

- [x] **Step 5: Commit the Quarkus adapter**

```bash
git add jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-web-quarkus-runtime
git commit -m "fix(web): classify Quarkus validation parameter sources"
```

### Task 3: Refactor And Complete Helidon Request Validation

**Files:**
- Modify: `jfoundry-runtime/jfoundry-helidon/runtime/jfoundry-web-helidon-runtime/src/main/java/org/jfoundry/web/helidon/ProblemDetailsExceptionMappers.java`
- Modify: `jfoundry-runtime/jfoundry-helidon/runtime/jfoundry-web-helidon-runtime/src/test/java/org/jfoundry/web/helidon/ProblemDetailsExceptionMapperTest.java`

- [x] **Step 1: Add failing Helidon/JAX-RS validation coverage**

Mirror the portable request-source cases from Task 2 using standard JAX-RS annotations. Preserve the existing
internal-service and return-value tests. Add a mixed violation test proving that return-value validation prevents
partial `400` conversion.

- [x] **Step 2: Run the focused Helidon test and verify failure**

Run:

```bash
mvn -pl jfoundry-runtime/jfoundry-helidon/runtime/jfoundry-web-helidon-runtime -am \
  -Dtest=ProblemDetailsExceptionMapperTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-body cascaded properties incorrectly receive pointers or the new expectations fail.

- [x] **Step 3: Implement conservative Helidon parameter provenance**

Use `JakartaRequestValidationErrors.from` after `isResourceRequestValidation` succeeds. Resolve the executable and
parameter index from Jakarta Validation path nodes. Standard JAX-RS parameter-binding and context annotations are
detail-only; an ordinary unbound entity parameter is document eligible; unknown metadata is detail-only. Keep
resource-root and return-value exclusion in the Helidon adapter.

- [x] **Step 4: Run all Helidon Web runtime tests**

```bash
mvn -pl jfoundry-runtime/jfoundry-helidon/runtime/jfoundry-web-helidon-runtime -am test
```

Expected: all tests pass.

- [x] **Step 5: Commit the Helidon adapter**

```bash
git add jfoundry-runtime/jfoundry-helidon/runtime/jfoundry-web-helidon-runtime
git commit -m "fix(web): classify Helidon validation parameter sources"
```

### Task 4: Cover Spring MVC Method Validation

**Files:**
- Modify: `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring/pom.xml`
- Modify: `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring/src/main/java/org/jfoundry/webmvc/spring/ProblemDetailsExceptionHandler.java`
- Modify: `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring/src/test/java/org/jfoundry/webmvc/spring/ProblemDetailsExceptionHandlerTest.java`

- [x] **Step 1: Add failing MockMvc method-validation tests**

Configure standalone MockMvc with a Bean Validation provider. Add controller methods covering request body,
request parameter, path variable, header, cookie, matrix variable, request part, model attribute, body container
elements, cross-parameter validation, and constrained return values. Assert body paths have pointers and every
other parameter source has detail only. Assert malformed JSON and conversion errors remain
`urn:jfoundry:problem:http-bad-request`.

- [x] **Step 2: Run the focused Spring test and verify failure**

```bash
mvn -pl jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring -am \
  -Dtest=ProblemDetailsExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `HandlerMethodValidationException` is handled as a generic bad request without the validation `errors`
contract.

- [x] **Step 3: Implement `HandlerMethodValidationException` handling**

Override `handleHandlerMethodValidationException`. If `exception.isForReturnValue()` is true, rethrow it so normal
server-error handling applies. Otherwise use `visitResults` and a visitor whose `requestBody` and
`requestBodyValidationResult` methods allow field/container paths, while cookie, matrix, model, path, header,
request parameter, request part, and `other` methods create detail-only errors. Append cross-parameter resolvables
as detail-only errors. Resolve messages through Spring's configured `MessageSource` and current locale. Render the
shared `RequestValidationProblem` descriptor.

- [x] **Step 4: Run all Spring Web MVC tests**

```bash
mvn -pl jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring -am test
```

Expected: all tests pass.

- [x] **Step 5: Commit the Spring adapter**

```bash
git add jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring
git commit -m "fix(webmvc): map Spring method validation problems"
```

### Task 5: Extend Runtime Integration Probes And Documentation

**Files:**
- Modify: `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests/src/main/java/org/jfoundry/quarkus/integration/ProblemDetailsResource.java`
- Modify: `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests/src/test/java/org/jfoundry/quarkus/integration/ProblemDetailsResourceTest.java`
- Modify: `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests/src/main/java/org/jfoundry/helidon/integration/ProblemDetailsResource.java`
- Modify: `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests/src/test/java/org/jfoundry/helidon/integration/ProblemDetailsResourceTest.java`
- Modify: `docs/i18n/en/capabilities/web.md`
- Modify: `docs/i18n/zh/capabilities/web.md`
- Modify: `docs/i18n/en/framework/framework-boundaries.md`
- Modify: `docs/i18n/zh/framework/framework-boundaries.md`
- Modify: `skills/maintain-jfoundry-framework/SKILL.md`
- Modify: `skills/maintain-jfoundry-framework/references/module-boundaries.md`

- [x] **Step 1: Add real-runtime request-source probes**

Add endpoints and HTTP assertions proving JSON body pointers and detail-only query/path/header validation in both
Jakarta REST runtimes. Keep response assertions limited to the agreed RFC 9457 members and `errors` shape.

- [x] **Step 2: Update English and Chinese documentation**

Document the validation/parsing boundary, pointer provenance, return-value exclusion, and cross-runtime parity.
Clarify that stable Jakarta specification APIs may be used by runtime-neutral infrastructure adapters while
container lifecycle and dispatch mechanisms remain runtime-specific. Keep the two language documents conceptually
aligned and do not mix languages within a document.

- [x] **Step 3: Run JVM integration verification**

```bash
mvn -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -am -Pjvm-integration verify
mvn -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify
```

Expected: both commands pass and the new HTTP assertions observe the same public contract.

Result: the Quarkus and Helidon integration-test modules passed their regular `test` phase, including the new
HTTP assertions. The Quarkus `jvm-integration` profile was also attempted, but Testcontainers could not start
its PostgreSQL resource because `/var/run/docker.sock` is unavailable. The equivalent Helidon profile was not
run because it requires the same unavailable Docker service.

- [x] **Step 4: Commit integration coverage and docs**

```bash
git add jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests \
  jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests \
  docs/i18n/en docs/i18n/zh skills/maintain-jfoundry-framework
git commit -m "test(web): verify request validation across runtimes"
```

### Task 6: Fix Clean Quarkus Reactor Resolution And Verify The Branch

**Files:**
- Modify: `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests/pom.xml`

- [x] **Step 1: Reproduce with a clean temporary repository**

```bash
clean_repo=$(mktemp -d)
mvn -Dmaven.repo.local="$clean_repo" -pl jfoundry-runtime/jfoundry-quarkus -am test
```

Expected before the fix: Quarkus `generate-code` or extension descriptor generation cannot resolve one or more
same-reactor `*-quarkus-deployment:1.3.0-SNAPSHOT` artifacts.

Result: the clean-repository build completed all 111 reactor modules successfully. The expected resolution
failure is no longer reproducible on the completed branch.

- [x] **Step 2: Apply the smallest reactor fix**

Enable Quarkus bootstrap workspace discovery for the integration-test consumer so augmentation resolves
same-reactor runtime and deployment projects from the Maven workspace during the `test` phase. Preserve the
standard deployment-to-runtime Maven dependency and do not create a runtime-to-deployment dependency cycle. Keep
`scripts/verify-ci-matrix.sh` on `mvn test` so the local release-baseline command continues to reproduce the CI
phase exactly.

Result: no build change was applied because workspace resolution already succeeds without an explicit Quarkus
workspace-discovery override. Adding the property without a failing case would not be a justified fix.

- [x] **Step 3: Re-run the clean-repository build**

Run the command from Step 1 with a new temporary repository. Expected: success without any preinstalled JFoundry
snapshot artifacts.

Result: the first clean-repository command already produced the expected successful result, so a second
identical download and build was unnecessary.

- [x] **Step 4: Run focused and release-baseline verification**

```bash
mvn validate
mvn test
scripts/verify-ci-matrix.sh
```

Then run the runtime Native Image stages selected by `skills/maintain-jfoundry-framework/references/testing.md`
for the shared Spring, Quarkus, and Helidon Web behavior. Expected: every command succeeds on Java 25.

Result: `mvn validate`, `mvn test`, the dependency-boundary fixture and repository checks, and
`scripts/verify-ci-matrix.sh` all passed on Java 25. Spring and Helidon Native Image builds and startup probes
passed. Quarkus Native Image and the PostgreSQL middleware profiles could not run because the local Docker
daemon is unavailable; `verify-runtime-ci.sh quarkus --stage native` confirmed that environment prerequisite.

- [x] **Step 5: Commit build changes and final corrections**

```bash
git add pom.xml jfoundry-runtime scripts docs skills jfoundry-boms jfoundry-core
git commit -m "build(quarkus): support clean reactor extension builds"
```

Result: no Quarkus build change was necessary. The final correction commit fixed Spring MVC provenance for
`MethodArgumentNotValidException` raised by model attributes and request parts, centralized deterministic error
sorting, and completed RFC 3986 percent-encoding for JSON Pointer URI fragments.

- [ ] **Step 6: Push and monitor the merge gate**

```bash
git push
gh pr checks 128 --watch --fail-fast=false
```

Expected: the GitHub Merge gate and all required runtime jobs pass.
