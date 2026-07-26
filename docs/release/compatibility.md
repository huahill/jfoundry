# Compatibility Matrix

## First Release Line

| Area | Supported Baseline |
|------|--------------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Spring Boot | 3.5.x |
| Spring Framework | 6.2.x |
| Quarkus | 3.37.3 |
| Helidon MP | 4.5.1 |
| Maven release tool | 3.9.x |
| Maven 4 | Compatibility check only while Maven 4 is preview/RC |

## First Release Dependency Baseline

| Dependency | Version |
|------------|---------|
| Spring Boot | 3.5.16 |
| Spring Framework | 6.2.19 |
| Spring Cloud | 2025.0.3 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Quarkus | 3.37.3 |
| Helidon MP | 4.5.1 |
| MyBatis-Plus | 3.5.16 |
| MyBatis-Plus Spring Boot 3 starter | 3.5.16 |
| Jackson | 2.21.4 |
| Spring Kafka | 3.3.16 |
| Spring AMQP | 3.2.12 |
| JobRunr | 8.7.1 |
| Redisson | 4.6.1 |
| RocketMQ client | 5.5.0 |
| Javassist override | 3.30.2-GA |
| Helidon `groovy-all` compatibility override | 2.4.14 |

Every business application aligning to this matrix imports `jfoundry-dependencies` and adds only the
documented starters or runtime capabilities it needs. Spring Boot, Quarkus, and Helidon applications
add exactly their matching runtime BOM alongside the core BOM. Runtime BOMs manage platform ecosystem
versions only; they do not manage JFoundry module versions. They may manage official Cloud or
integration BOMs that are compatible with the selected runtime baseline, but they do not add those
libraries to an application. Applications still select each starter or client explicitly. Do not
import every starter or runtime capability into a business application by default.

`org.javassist:javassist` is managed explicitly because `rocketmq-client:5.5.0` brings
`rocketmq-remoting -> reflections:0.9.11 -> javassist:3.21.0-GA`, whose POM emits a
Maven 4 model warning. Maven 4 also reports imported-BOM conflicts from supported runtime
ecosystems; the compatibility gate verifies successful package resolution rather than requiring
a warning-free effective model.

`jfoundry-helidon-dependencies` manages `org.codehaus.groovy:groovy-all:2.4.14` as a narrow
platform-local compatibility override required by Maven release dependency validation. It is not a
general JFoundry dependency-management rule.

## Verification Evidence

Historic evidence was recorded on 2026-06-27 with local Java `21.0.10-tem` and Maven wrapper `3.9.16`. The current release-baseline evidence was recorded on 2026-07-24 with GraalVM Community `25.0.2` and Maven wrapper `3.9.16`.

| Gate | Command | Result |
|------|---------|--------|
| Unit tests | `./mvnw -B clean test` | PASS on Java 25 |
| Package artifacts | `./mvnw -B -DskipTests package` | PASS on Java 25 |
| Spring middleware integration tests | `./mvnw -B -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Spring Native Image consumer smoke test | GraalVM 25, `./mvnw -B -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests -am -Pnative package`, then `GET /jfoundry/native/ready` | PASS on GraalVM Community 25.0.2 |
| Quarkus PostgreSQL middleware integration | `./mvnw -B -pl jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Helidon PostgreSQL/JTA middleware integration | `./mvnw -B -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify` | PASS on Java 25 with Docker 29.6.2/Testcontainers |
| Release guard | `mvn -Prelease -DskipTests validate` | Expected fail fast on `Release builds require non-SNAPSHOT project versions.` |
| Maven 4 validate | Maven `4.0.0-rc-5`, `mvn -B -DskipTests validate -e` | PASS |
| Maven 4 package | Maven `4.0.0-rc-5`, `mvn -B -DskipTests package` | PASS on 2026-07-24; Maven 4 reports imported-BOM model warnings |
| Quarkus JVM consumer smoke test | Install runtime/deployment artifacts, then `mvn -pl jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pjvm-integration verify` | Historical PASS on Java 21; Java 25 revalidation is required by the release baseline |
| Helidon Native CDI/Web consumer smoke test | GraalVM 25, `mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pnative-image package`, then HTTP Problem Details smoke | PASS on 2026-07-24 |

GitHub Actions runs the Java 25 release baseline. Helidon Native verification also uses GraalVM
Community 25.

Helidon MP 4.5.1 Narayana JTA Native Image support is experimental. On GraalVM Community 25.0.2 for
macOS ARM64, adding Helidon's JPA integration also fails image generation through
`JpaExtension.processPersistenceXmls`, with `org.xml.sax.helpers.LocatorImpl` retained in the image
heap. The Native CDI/Web consumer starts and serves the JFoundry Problem Details response, but
`TransactionRunner` execution fails because Helidon's CDI transaction-manager delegate is not
initialized in the image. The environment, JVM PostgreSQL/JTA/JPA control result, and Native failure
trace are recorded in [Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).
JVM Helidon JTA is supported; Native JTA and JPA are not release acceptance claims until upstream
support works.

## Future Framework Upgrade Line

Spring Boot 4.x should be handled as a separate compatibility line, not folded into the first
release baseline merely because the repository now compiles on Java 25. As of 2026-06-27, Spring Boot
4.0.7 remains a stable 4.0 maintenance release, and Spring Boot 4.1 supports Java versions
up to Java 26. JDK 25 reached General Availability on 2025-09-16.

| Area | Target Baseline |
|------|-----------------|
| Java compile target | 25 |
| Runtime Java | 25 |
| Spring Boot | 4.x |
| Spring Framework | 7.x |
| Jakarta EE | 11 via Spring Boot 4 dependencies |
| Maven release tool | Maven 3.9.x until Maven 4 GA |
| Maven 4 | Compatibility matrix first, release tool only after GA evidence |

Treat this framework-upgrade line as a separate compatibility track until the repository records full
Spring Boot 4.x, Spring Framework 7.x, Maven, and CI evidence.

## Release Gates

- `./mvnw test`
- `./mvnw -DskipTests package`
- `./mvnw -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify`
- Java 25 release-baseline test in CI
- Maven 4 compatibility matrix in CI
- Spring and Quarkus Native Image smoke tests in CI
- Quarkus and Helidon PostgreSQL middleware integration tests in CI
- Helidon Native CDI/Web smoke test with GraalVM 25; do not gate on Helidon Native JTA until its
  upstream implementation becomes supported
- Maven Central metadata guard in the `release` profile
