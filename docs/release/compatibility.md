# Compatibility Matrix

## Supported Release Line

| Area | Supported Baseline |
|------|--------------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Spring Boot-only line | Spring Boot 4.1.x |
| Spring Cloud line | Spring Boot 4.0.7, Spring Cloud 2025.1.2, Spring Cloud Alibaba 2025.1.0.0 |
| Quarkus | 3.39.1 |
| Helidon MP | 4.5.3 |
| Maven source descriptor model | 4.1.0 (Maven 4-only YAML model) |
| Maven build, Consumer POM, and publication tool | 4.0.0-rc-6 (Maven 4-only; publication blocked until final) |
| Maven 3 source-build compatibility | Not supported |

## Dependency Baseline

| Dependency | Version |
|------------|---------|
| Spring Boot-only | 4.1.1 |
| Spring Cloud line Spring Boot | 4.0.7 |
| Spring Cloud line Spring Cloud | 2025.1.2 |
| Spring Cloud line Spring Cloud Alibaba | 2025.1.0.0 |
| Quarkus | 3.39.1 |
| Helidon MP | 4.5.3 |
| MyBatis-Plus | 3.5.17 |
| JSpecify | 1.0.1 |
| MyBatis-Plus Spring Boot 4 starter | 3.5.17 |
| Jackson 3 | 3.2.2 |
| Helidon Jackson annotations compatibility override | 2.22 |
| Jakarta Persistence | 3.2.0 |
| Hibernate ORM | 7.2.19.Final |
| Spring Kafka | 4.0.6 |
| Spring AMQP | 4.0.4 |
| JobRunr | 8.8.2 |
| Redisson | 4.7.0 |
| RocketMQ client | 5.5.0 |
| Javassist override | 3.30.2-GA |
| Helidon `groovy-all` compatibility override | 3.0.25 |

Every business application aligning to this matrix imports `jfoundry-dependencies` and adds only the
documented starters or runtime capabilities it needs. Spring Boot-only applications use
`jfoundry-spring-boot-dependencies`; Spring Cloud applications use their own or a standard Maven
parent for Spring Boot 4.0.7 and explicitly import `jfoundry-spring-cloud-dependencies`. Applications
with another Maven parent import exactly one matching runtime BOM before `jfoundry-dependencies`.
The two Spring runtime BOMs must not be combined. Runtime BOMs manage platform ecosystem versions
only; they do not manage JFoundry module versions or Foundation versions. Applications still select
each starter or client explicitly. Do not import every starter or runtime capability into a business
application by default.

JFoundry manages the MyBatis-Plus Boot 4 starter as part of the MyBatis-Plus component family. It does
not independently manage the starter's `org.mybatis:mybatis-spring` transitive dependency; that bridge
version follows the tested MyBatis-Plus starter dependency graph.

JFoundry's Spring messaging starter uses Spring Boot's `spring-boot-starter-json`, which provides
Jackson 3 as Spring Boot 4's default JSON mapper. The shipped JFoundry serializer and jMolecules
integration use `tools.jackson.databind.ObjectMapper`; Outbox inherits this capability through the
messaging starter. Jackson 2 compatibility modules are not supported by JFoundry.

`org.javassist:javassist` is managed explicitly because `rocketmq-client:5.5.0` brings
`rocketmq-remoting -> reflections:0.9.11 -> javassist:3.21.0-GA`, whose POM emits a
Maven 4 model warning. Maven 4 also reports imported-BOM conflicts from supported runtime
ecosystems; the compatibility gate verifies successful package resolution rather than requiring
a warning-free effective model.

`jfoundry-helidon-dependencies` has two narrow platform-local compatibility overrides. It manages
`org.codehaus.groovy:groovy-all:3.0.25` for Maven release dependency validation and
`com.fasterxml.jackson.core:jackson-annotations:2.22` because Helidon's managed 2.21 line is not
compatible with Foundation's Jackson Databind 3.2.2. These are not general JFoundry
dependency-management rules.

## Verification Evidence

The complete 122-project Mason YAML proof of concept was verified on 2026-08-28 with the Maven 4.1.0 source descriptor model, GraalVM Community `25.0.4`, Maven `4.0.0-rc-6`, Mason `0.3.0`, and Docker Desktop `29.7.2`; Quarkus tests consume Maven's resolved model through `generate-code-tests`. See [Mason YAML Proof of Concept](mason-yaml-poc.md).

| Gate | Command | Result |
|------|---------|--------|
| Unit tests | `./mvnw -B clean test` | PASS on Java 25 |
| Package artifacts | `./mvnw -B -DskipTests package` | PASS on Java 25 |
| Spring middleware integration tests | `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Spring Native Image base Starter/Web MVC consumer smoke test | GraalVM 25 with the Boot-only Spring Boot 4.1.1 AOT line, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative package`, then `GET /jfoundry/native/ready` | PASS on 2026-08-28 from the complete Mason YAML tree |
| Spring Native Image MyBatis-Plus persistence integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, MyBatis-Plus 3.5.17 and its `mybatis-plus-spring-boot-native-image` module, PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-mybatis-plus verify` | PASS on 2026-08-28 from the complete Mason YAML tree |
| Spring Native Image Redisson lock integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, Redisson 4.7.0, and Redis, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-redisson verify` | PASS on 2026-08-28 from the complete Mason YAML tree |
| Spring Native Image JobRunr Outbox integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, JobRunr 8.8.2, and PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-jobrunr clean verify` | FAIL on both the XML baseline and Mason tree: JobRunr passes `resource:/resources`, which GraalVM 25.0.4 rejects because the URI has no Native Image resource root identifier |
| Quarkus PostgreSQL middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Helidon PostgreSQL/JTA middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Release guard | `mvn -Prelease -DskipTests validate` | Expected fail fast on `Release builds require non-SNAPSHOT project versions.` |
| Maven 4 validate | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests validate -e` | PASS |
| Maven 4 package | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests package` | PASS on 2026-08-24; Maven 4 reports imported-BOM model warnings |
| Maven Consumer POM contract | Maven `4.0.0-rc-6`, clean `install`, then `scripts/verify-consumer-pom.sh` with Maven 4 | PASS on 2026-08-24; verifies flattened child POMs, both direct Spring BOM lines, the Boot parent, and Cloud Alibaba versionless resolution |
| Mason YAML full reactor | `scripts/verify-mason-poc.sh`, focused tests, `scripts/verify-mason-model-equivalence.sh a744b26a`, and `scripts/verify-ci-matrix.sh` | PASS on 2026-08-29 for 122 YAML projects, aggregate effective-model equivalence, warning equivalence, and the complete Java 25 matrix; the comparison uses the immutable post-release XML baseline and excludes the documented Quarkus `generate-code-tests` and YAML-test classpath handoffs |
| Maven 4 Central publication readiness | Release workflow `Verify Maven 4 Central readiness` | BLOCKED while wrapper is RC6 or `MAVEN_CENTRAL_MAVEN4_READY` is not `true` |
| Mason reactor version update | `scripts/set-mason-reactor-version-test.sh` plus a disposable full-tree update | PASS on 2026-08-28; 123 classified references updated across 122 POMs while comments and output timestamps remained unchanged |
| Spring Cloud BOM resolution | Versionless Spring Cloud Alibaba Nacos Discovery consumer with `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies` | Required before Central deploy; rejects the unsupported Spring Boot 4.1.1 plus Spring Cloud 2025.1.2 combination |
| Quarkus JVM consumer smoke test | Install runtime/deployment artifacts, then `mvn -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pjvm-integration verify` | PASS on Java 25 with PostgreSQL on 2026-08-28 from the complete Mason YAML tree |
| Helidon Native CDI/Web server consumer smoke test | GraalVM 25, `mvn -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pnative-image package`, then HTTP Problem Details smoke | PASS on 2026-08-25 with GraalVM Community 25.0.4 |

GitHub Actions runs the Java 25 release baseline. Helidon Native verification also uses GraalVM
Community 25.

Aggregate effective-model and warning equivalence are migration-time POC evidence against the immutable
pre-conversion baseline.
They are not continuous release gates that require retaining or reconstructing an XML baseline.
Sustainable checks instead enforce root-reactor reachability for all 122 YAML projects, converter
and version-update behavior, the guarded Maven 4 publication flow in the release workflow, and the normal
Java/runtime matrix.

Helidon MP 4.5.3 Narayana JTA Native Image support is experimental. On GraalVM Community 25.0.2 for
macOS ARM64, adding Helidon's JPA integration also fails image generation through
`JpaExtension.processPersistenceXmls`, with `org.xml.sax.helpers.LocatorImpl` retained in the image
heap. The Native CDI/Web consumer starts and serves the JFoundry Problem Details response, but
`TransactionRunner` execution fails because Helidon's CDI transaction-manager delegate is not
initialized in the image. The environment, JVM PostgreSQL/JTA/JPA control result, and Native failure
trace are recorded in [Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).
JVM Helidon JTA is supported; Native JTA and JPA are not release acceptance claims until upstream
support works.

Helidon MP 4.5.3 REST Client is also JVM-only in the current acceptance matrix. Adding
`helidon-microprofile-rest-client` to the GraalVM Community 25.0.4 image classpath fails during
initialization because Helidon's `RestClientSubstitution.ReflectionUtilSubstitution` contains an
unannotated substitution method. JFoundry keeps `jfoundry-restclient-helidon` in JVM integration-test
scope; the Native CDI/Web server smoke excludes the REST Client capability and does not claim Native
REST Client support.

## Release Gates

- `./mvnw test`
- `./mvnw -DskipTests package`
- `./mvnw -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify`
- Java 25 release-baseline test in CI
- Maven 4 Wrapper package compatibility and Consumer POM contract before Central deployment
- Mason source and 122-project reactor-reachability checks, converter and version-update tests, Quarkus model-handoff checks, and the guarded Maven 4 release workflow
- Spring and Quarkus Native Image smoke tests in CI
- Spring Native Image MyBatis-Plus persistence integration in CI
- Spring Native Image Redisson lock and JobRunr Outbox integrations in CI
- Quarkus and Helidon PostgreSQL middleware integration tests in CI
- Helidon Native CDI/Web smoke test with GraalVM 25; do not gate on Helidon Native JTA until its
  upstream implementation becomes supported
- Maven Central metadata guard in the `release` profile
