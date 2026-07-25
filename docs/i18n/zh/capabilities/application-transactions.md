# 应用事务

`TransactionRunner` 是 jfoundry 的应用层事务边界契约。当应用编排需要显式事务代码块，但不希望依赖 Spring `TransactionTemplate` 时，可以直接使用：

```java
transactionRunner.run(TransactionOptions.builder()
        .timeout(Duration.ofSeconds(10))
        .build(), () -> {
    orderRepository.save(order);
    outboxRecorder.record(events);
});
```

运行时集成可以提供声明式事务边界，但它不能替代运行时的基础设施事务机制。应用事务边界应保持在 Use Case 或 Application Service 边界。

如果同一方法还需要分布式锁，advisor 执行顺序见 [分布式锁](distributed-locks.md)。

Spring 运行时装配、用户覆盖和注解配置见 [Spring Boot 运行时装配](../implementations/spring-boot.md)。精确的启动器、配置项和自动配置条件见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。Quarkus CDI 与 Jakarta Transactions 行为，以及不能移植到 JTA 的选项，见 [Quarkus 运行时集成](../implementations/quarkus.md)。
