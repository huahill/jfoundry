# Application Transactions

`TransactionRunner` is jfoundry's primary contract for application-layer transaction boundaries. Use
it when application orchestration needs an explicit transaction block without depending on Spring
`TransactionTemplate`. Callbacks and `run` / `call` do not declare checked exceptions; runtime
exceptions propagate unchanged. Translate adapter-level checked failures such as `SQLException`
before they enter the callback.

```java
transactionRunner.run(TransactionOptions.builder()
        .timeout(Duration.ofSeconds(10))
        .build(), () -> {
    orderRepository.save(order);
    outboxRecorder.record(events);
});
```

Runtime integrations can expose declarative transaction boundaries, but they do not replace a
runtime's infrastructure-level transaction mechanism. Keep the application transaction boundary at
the use-case or application service boundary.

When a method also needs a distributed lock, see [Distributed Locks](distributed-locks.md) for the
advisor ordering.

For Spring runtime assembly, user overrides, and annotation configuration, see
[Spring Boot Runtime Assembly](../implementations/spring-boot.md). The exact starter, property, and
auto-configuration conditions are in the
[Spring Boot Auto-configuration reference](../reference/spring-boot-autoconfiguration.md).
For Quarkus CDI and Jakarta Transactions behavior, including options that are not portable to JTA,
see [Quarkus Runtime Integration](../implementations/quarkus.md).
