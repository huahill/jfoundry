# Compatibility Matrix

## Stable 1.x Line

| Area | Supported Baseline |
|------|--------------------|
| Java compile target | 21 |
| Runtime Java | 21 |
| Java 25 | CI compatibility target |
| Spring Boot | 3.5.x |
| Spring Framework | 6.2.x |
| Quarkus | 3.37.3 |
| Helidon MP | 4.5.1 |
| Maven release tool | 3.9.x |
| Maven 4 | Compatibility check only while Maven 4 is preview/RC |

## Stable 1.x Dependency Baseline

| Dependency | Version |
|------------|---------|
| Spring Boot | 3.5.16 |
| Spring Framework | 6.2.19 |
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

Business applications aligning to this matrix should prefer the Spring dependency BOM and add
runtime starters explicitly by capability. Quarkus and Helidon applications should instead import
their respective runtime BOM and add only the documented runtime capabilities. Do not import every
starter or runtime capability into a business application by default.

`org.javassist:javassist` is managed explicitly because `rocketmq-client:5.5.0` brings
`rocketmq-remoting -> reflections:0.9.11 -> javassist:3.21.0-GA`, whose POM emits a
Maven 4 model warning. Maven 4 also reports imported-BOM conflicts from supported runtime
ecosystems; the compatibility gate verifies successful package resolution rather than requiring
a warning-free effective model.

## Verification Evidence

Recorded on 2026-06-27 with local Java `21.0.10-tem` and Maven wrapper `3.9.16`.

| Gate | Command | Result |
|------|---------|--------|
| Unit tests | `./mvnw test` | PASS |
| Package artifacts | `./mvnw -DskipTests package` | PASS |
| Integration tests | `./mvnw -pl jfoundry-verification/jfoundry-middleware-integration-tests -am -Pit verify` | PASS, 28 integration tests with Docker 29.5.3/Testcontainers |
| Release guard | `mvn -Prelease -DskipTests validate` | Expected fail fast on `Release builds require non-SNAPSHOT project versions.` |
| Maven 4 validate | Maven `4.0.0-rc-5`, `mvn -B -DskipTests validate -e` | PASS |
| Maven 4 package | Maven `4.0.0-rc-5`, `mvn -B -DskipTests package` | PASS on 2026-07-24; Maven 4 reports imported-BOM model warnings |
| Quarkus JVM consumer smoke test | Install runtime/deployment artifacts, then `mvn -pl jfoundry-runtime-integrations/jfoundry-quarkus/integration-tests/jfoundry-quarkus-integration-tests -Pjvm-integration verify` | PASS on Java 21 |
| Helidon Native CDI/Web consumer smoke test | GraalVM 25, `mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/integration-tests/jfoundry-helidon-integration-tests -am -Pnative-image package`, then HTTP Problem Details smoke | PASS on 2026-07-24 |

The Java 25 compatibility matrix remains a GitHub Actions release gate. Helidon Native verification
also uses GraalVM Community 25; this does not change the Java 21 compilation and runtime baseline.

Helidon MP 4.5.1 Narayana JTA Native Image support is experimental. The Helidon Native consumer
starts and serves the JFoundry Problem Details response, but `TransactionRunner` execution fails
because Helidon's CDI transaction-manager delegate is not initialized in the image. JVM Helidon JTA
is supported; Native JTA execution is not a release acceptance claim until upstream support works.

## Future 2.x Line

Spring Boot 4.x and Java 25 should be handled as a 2.x compatibility line, not folded into
the first 1.x Central release. As of 2026-06-27, Spring Boot 4.1.0 is available, Spring Boot
4.0.7 remains a stable 4.0 maintenance release, and Spring Boot 4.1 supports Java versions
up to Java 26. JDK 25 reached General Availability on 2025-09-16.

| Area | Target Baseline |
|------|-----------------|
| Java compile target | 25 |
| Spring Boot | 4.x |
| Spring Framework | 7.x |
| Jakarta EE | 11 via Spring Boot 4 dependencies |
| Maven release tool | Maven 3.9.x until Maven 4 GA |
| Maven 4 | Compatibility matrix first, release tool only after GA evidence |

Treat the 2.x line as a separate compatibility track until the repository records full Spring Boot
4.x, Spring Framework 7.x, Maven, and CI evidence.

## Release Gates

- `./mvnw test`
- `./mvnw -DskipTests package`
- `./mvnw -pl jfoundry-verification/jfoundry-middleware-integration-tests -am -Pit verify`
- Java 25 runtime matrix for the 1.x line in CI
- Maven 4 compatibility matrix in CI
- Quarkus Native Image smoke test in CI
- Helidon Native CDI/Web smoke test with GraalVM 25; do not gate on Helidon Native JTA until its
  upstream implementation becomes supported
- Maven Central metadata guard in the `release` profile
