# Compatibility Matrix

## Supported Release Line

| Area | Supported Baseline |
|------|--------------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Spring Boot-only line | Spring Boot 4.1.x |
| Spring Cloud line | Spring Boot 4.0.7, Spring Cloud 2025.1.2, Spring Cloud Alibaba 2025.1.0.0 |
| Quarkus | 3.38.3 |
| Helidon MP | 4.5.3 |
| Maven release tool | 4.0.0-rc-6 (experimental) |
| Maven 3.9 | Consumer compatibility check |

## Dependency Baseline

| Dependency | Version |
|------------|---------|
| Spring Boot-only | 4.1.1 |
| Spring Cloud line Spring Boot | 4.0.7 |
| Spring Cloud line Spring Cloud | 2025.1.2 |
| Spring Cloud line Spring Cloud Alibaba | 2025.1.0.0 |
| Quarkus | 3.38.3 |
| Helidon MP | 4.5.3 |
| MyBatis-Plus | 3.5.17 |
| MyBatis-Plus Spring Boot 4 starter | 3.5.17 |
| Jackson 3 | 3.2.2 |
| Helidon Jackson annotations compatibility override | 2.22 |
| Jakarta Persistence | 3.2.0 |
| Hibernate ORM | 7.2.19.Final |
| Spring Kafka | 4.0.6 |
| Spring AMQP | 4.0.4 |
| JobRunr | 8.8.1 |
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

Historic evidence was recorded on 2026-06-27 with local Java `21.0.10-tem` and Maven wrapper `3.9.16`. The current release-baseline evidence was recorded on 2026-07-24 with GraalVM Community `25.0.2` and Maven wrapper `3.9.16`. The Spring MyBatis-Plus, Redisson, and JobRunr Native Image verification evidence was recorded on 2026-07-30 with the same GraalVM and Maven versions. HTTP logging runtime verification was refreshed on 2026-08-25 with GraalVM Community `25.0.4` and Maven wrapper `4.0.0-rc-6`. Maven 4 Consumer POM release verification was added on 2026-08-01 and now runs with RC6; ordinary CI and the release workflow perform a clean Maven 4 install and verify the installed POMs with Maven 3.9 and Maven 4 before deployment.

| Gate | Command | Result |
|------|---------|--------|
| Unit tests | `./mvnw -B clean test` | PASS on Java 25 |
| Package artifacts | `./mvnw -B -DskipTests package` | PASS on Java 25 |
| Spring middleware integration tests | `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Spring Native Image base Starter/Web MVC consumer smoke test | GraalVM 25 with the Boot-only Spring Boot 4.1.1 AOT line, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative package`, then `GET /jfoundry/native/ready` | Requires CI revalidation after the 4.1.1 baseline upgrade |
| Spring Native Image MyBatis-Plus persistence integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, MyBatis-Plus 3.5.17 and its `mybatis-plus-spring-boot-native-image` module, PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-mybatis-plus verify` | Requires CI revalidation after the 4.1.1 baseline upgrade |
| Spring Native Image Redisson lock integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, Redisson 4.7.0, and Redis, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-redisson verify` | Pending Docker-backed verification; the Spring starter uses the Foundation BOM's 4.7.0 constraint |
| Spring Native Image JobRunr Outbox integration | GraalVM 25 with Boot-only Spring Boot 4.1.1, JobRunr 8.8.1, and PostgreSQL, `./mvnw -B -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative-jobrunr clean verify` | Pending Docker-backed verification; dependency resolution aligns the starter and core library at 8.8.1 |
| Quarkus PostgreSQL middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Helidon PostgreSQL/JTA middleware integration | `./mvnw -B -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Release guard | `mvn -Prelease -DskipTests validate` | Expected fail fast on `Release builds require non-SNAPSHOT project versions.` |
| Maven 4 validate | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests validate -e` | PASS |
| Maven 4 package | Maven `4.0.0-rc-6`, `./mvnw -B -DskipTests package` | PASS on 2026-08-24; Maven 4 reports imported-BOM model warnings |
| Maven Consumer POM contract | Maven `4.0.0-rc-6`, clean `install`, then `scripts/verify-consumer-pom.sh` with Maven 3.9 and Maven 4 RC6 | PASS on 2026-08-24; verifies flattened child POMs, both direct Spring BOM lines, the Boot parent, and Cloud Alibaba versionless resolution with Maven 3.9 and Maven 4 |
| Spring Cloud BOM resolution | Versionless Spring Cloud Alibaba Nacos Discovery consumer with `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies` | Required before Central deploy; rejects the unsupported Spring Boot 4.1.1 plus Spring Cloud 2025.1.2 combination |
| Quarkus JVM consumer smoke test | Install runtime/deployment artifacts, then `mvn -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pjvm-integration verify` | Historical PASS on Java 21; Java 25 revalidation is required by the release baseline |
| Helidon Native CDI/Web server consumer smoke test | GraalVM 25, `mvn -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pnative-image package`, then HTTP Problem Details smoke | PASS on 2026-08-25 with GraalVM Community 25.0.4 |

GitHub Actions runs the Java 25 release baseline. Helidon Native verification also uses GraalVM
Community 25.

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
unannotated substitution method. JFoundry keeps that implementation in JVM integration-test scope;
the Web runtime exposes only the optional MicroProfile REST Client SPI and the Native CDI/Web server
smoke does not claim Native REST Client support.

## Release Gates

- `./mvnw test`
- `./mvnw -DskipTests package`
- `./mvnw -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify`
- Java 25 release-baseline test in CI
- Maven 4 Wrapper package compatibility and Consumer POM contract before Central deployment
- Spring and Quarkus Native Image smoke tests in CI
- Spring Native Image MyBatis-Plus persistence integration in CI
- Spring Native Image Redisson lock and JobRunr Outbox integrations in CI
- Quarkus and Helidon PostgreSQL middleware integration tests in CI
- Helidon Native CDI/Web smoke test with GraalVM 25; do not gate on Helidon Native JTA until its
  upstream implementation becomes supported
- Maven Central metadata guard in the `release` profile
