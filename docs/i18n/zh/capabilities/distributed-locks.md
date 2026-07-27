# 分布式锁

分布式锁提供框架无关的契约：`DistributedLockClient`、`LockExecutor`、`LockKey`、`LockOptions` 和 `@DistributedLock`。只有互斥本身是用例语义的一部分时才使用锁；它不能替代数据库一致性或聚合不变量。

编程式用法：

```java
lockExecutor.execute(
        new LockKey("order-confirmation", command.orderId()),
        LockOptions.builder()
                .waitTime(Duration.ofSeconds(2))
                .leaseTime(Duration.ofSeconds(10))
                .build(),
        () -> {
            applicationService.confirm(command);
            return null;
        });
```

`LockKey` 将非敏感 scope 与受保护资源的 value 分开。锁客户端使用稳定的哈希 backend name；诊断字符串和锁获取失败异常只包含 scope，不包含 value。应选择稳定的 scope 与 value，使独立工作可以并发，而相互冲突的工作必须互斥。不要在 scope 中放置敏感标识。

对于 `@DistributedLock`，Spring 使用声明类与方法推导 scope，注解的 key 表达式只提供 value。

`waitTime` 控制调用方等待获取锁的最长时间。所选 lock client 支持显式租期时，`leaseTime` 控制锁的生命周期。

无法获得锁时，默认 `LockFailureMode.THROW` 会抛出 `DistributedLockUnavailableException`。只有当“跳过执行”本身就是明确业务结果时，才使用 `failureMode = LockFailureMode.SKIP`。

## 与事务的执行顺序

用例同时需要分布式锁和事务时，应先获取锁，再在临界区内调用 `TransactionRunner`。这样可以避免在等待分布式锁期间提前打开数据库事务。

Spring Boot 运行时装配、所选锁客户端集成、用户覆盖和注解配置见 [Spring Boot 运行时装配](../implementations/spring-boot.md)。精确的启动器、配置项和自动配置条件见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。
