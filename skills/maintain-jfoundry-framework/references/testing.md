# Testing And Verification

## Choose The Narrowest Useful Test

Use module-scoped Maven commands first:

```bash
mvn -pl <module> test
```

Use `-am` when reactor dependencies must be built:

```bash
mvn -pl <module> -am test
```

For full confidence before release-sensitive changes:

```bash
mvn test
```

## Common Verification Targets

| Change | Verification |
|---|---|
| Domain model API | `mvn -pl jfoundry-domain test` |
| Architecture annotations | `mvn -pl jfoundry-core/jfoundry-architecture/jfoundry-hexagonal test` or relevant module |
| ArchUnit rules | `mvn -pl jfoundry-core/jfoundry-architecture/jfoundry-architecture-test test` |
| Application SPI/core | `mvn -pl jfoundry-core/jfoundry-application/<module> test` |
| Transaction core | `mvn -pl jfoundry-core/jfoundry-application/jfoundry-transaction-core test` |
| Outbox core | `mvn -pl jfoundry-core/jfoundry-application/jfoundry-outbox-core test` |
| Inbox core | `mvn -pl jfoundry-core/jfoundry-application/jfoundry-inbox-core test` |
| MyBatis-Plus adapter | `mvn -pl jfoundry-core/jfoundry-infrastructure/<module> -am test` |
| Broker sender runtime adapter | `mvn -pl <runtime messaging module> -am test` |
| Spring runtime adapter | `mvn -pl jfoundry-runtime-integrations/jfoundry-spring/runtime/<module> -am test` |
| Boot auto-configuration | `mvn -pl jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure -am test` |
| Spring middleware integration | `mvn -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests -am -Pit verify` |
| Quarkus JVM integration | `mvn -pl jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pjvm-integration verify` |
| Quarkus Native Image integration | `mvn -pl jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests -Pnative verify` |
| Helidon JVM integration | `mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pjvm-integration verify` |
| Helidon Native Image integration | `mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests -am -Pnative-image package` |
| Starter POM | `mvn -pl <starter-module> -am test` or `mvn validate` for dependency shape |

## Test Expectations

- Add focused unit tests near changed behavior.
- Add auto-configuration condition tests for new Boot wiring.
- Add ArchUnit self-tests for new architecture rules.
- Add persistence tests for store or repository behavior.
- Add concurrency tests for claim, retry, idempotency, or state transition changes.
- Add runtime-local middleware integration tests only when behavior requires a concrete runtime, real database, broker, Testcontainers, or Native Image verification; keep framework-neutral tests next to the core or infrastructure implementation.
- Mockito's Java agent is opt-in per module. When adding Mockito usage to test sources, or when a test framework loads Mockito during test startup, ensure the module has a test dependency that resolves `mockito-core` and override `mockito.javaagent.argLine` with `-javaagent:${org.mockito:mockito-core:jar}`. Do not enable the Mockito Java agent in modules that do not load Mockito during tests.

When changing public API, starter dependencies, configuration properties, table schemas, or release baselines, include compatibility impact in the final report even if tests pass.
