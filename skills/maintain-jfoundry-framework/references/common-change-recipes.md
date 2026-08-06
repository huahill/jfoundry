# Common Change Recipes

## Add A Broker Adapter

1. Add framework-neutral sender adapter under `jfoundry-core/jfoundry-infrastructure/jfoundry-messaging-<broker>`.
2. Implement `MessageSender`; do not change Outbox core for broker-specific details.
3. Add Spring Boot auto-configuration in the matching capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure` if conditional bean wiring is needed.
4. Add `jfoundry-messaging-<broker>-spring-boot-starter`.
5. Update BOM dependency management.
6. Add adapter unit tests and auto-configuration tests.
7. Add integration verification only if a real broker behavior must be proven.

## Add A Persistence Adapter

1. Keep domain/application contracts unchanged unless the abstraction is insufficient.
2. Place adapter code under `jfoundry-infrastructure`.
3. Keep runtime-specific wiring out of the adapter.
4. Add Spring Boot auto-configuration only in a capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure`.
5. Add a dedicated starter when users should opt in explicitly.
6. Add persistence tests and copyable SQL templates or DDL guidance where applicable. Do not introduce auto-run framework migrations.

## Change Outbox Or Inbox State Behavior

1. Start from core state model and service tests in `jfoundry-application`.
2. Preserve atomic claim, retry, terminal state, and idempotency semantics unless the change explicitly revises them.
3. Update MyBatis store tests when persistence behavior changes.
4. Update Spring dispatcher/recovery/cleanup tests when scheduling or transaction behavior changes.
5. Update docs and compatibility notes when table schema, state names, or configuration properties change.

## Add Or Change Spring Boot Auto-Configuration

1. Put auto-configuration classes in a capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure`.
2. Use `@AutoConfiguration` and `@Bean`; do not use component scanning for auto-configuration.
3. Add conditional tests covering present/missing dependencies and user-provided beans.
4. Keep adapter modules free of `AutoConfiguration.imports`.
5. Update the relevant starter if users need a dependency entry point.

## Add An ArchUnit Rule

1. Put reusable rules in `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test`.
2. Decide whether the rule belongs in a primary entrypoint such as `JFoundryRules.hexagonalStrict()` or should remain opt-in.
3. Add positive and negative fixtures.
4. Add self-tests proving the rule fails for invalid fixtures and passes for valid fixtures.
5. Update `../../../docs/i18n/en/framework/archunit-rules.md` and the matching localized page when the public rule behavior changes.

## Change A Starter

1. Check whether the capability should remain explicit.
2. Update only POM dependencies.
3. Keep Java behavior in runtime, auto-configuration, or adapter modules.
4. Update BOMs if new artifacts or versions are introduced.
5. Run Maven validation or module tests with `-am`.
6. Update README or business onboarding docs when starter guidance changes.

## Add Or Change Spring Runtime Integration

1. Put Spring Framework-specific runtime behavior under `jfoundry-runtime/jfoundry-spring/runtime/<module>`.
2. Keep the framework-neutral contract in `jfoundry-application` or `jfoundry-infrastructure` as appropriate.
3. Put conditional bean wiring and configuration properties in the matching capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure`.
4. Add or update a Spring Boot starter only when users need an explicit dependency entry point.
5. Add runtime adapter tests and auto-configuration condition tests.
