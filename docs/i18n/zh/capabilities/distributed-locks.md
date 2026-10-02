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

对于 `@DistributedLock`，每个运行时都使用声明类与方法推导 scope，注解的 key 只提供 value。不包含 `${` 或 `#{` 的 key 在三个运行时都是字面量。

Spring 使用 SpEL 求值。写成 `#orderId` 或 `'order:' + #orderId`。方法参数名以及 `p0`、`a0` 都可以使用。

Quarkus 和 Helidon 使用 Jakarta EL 求值。写成 `#{orderId}` 或 `order:#{orderId}`。同样可以使用方法参数名、`p0` 和 `a0`。不要把 Spring SpEL key 拿到这两个运行时使用，也不要期望 Spring 求值 Jakarta EL key。

`waitTime` 控制调用方等待获取锁的最长时间。所选 lock client 支持显式租期时，`leaseTime` 控制锁的生命周期。

无法获得锁时，默认 `LockFailureMode.THROW` 会抛出 `DistributedLockUnavailableException`。只有当“跳过执行”本身就是明确业务结果时，才使用 `failureMode = LockFailureMode.SKIP`。

## 与事务的执行顺序

用例同时需要分布式锁和事务时，应先获取锁，再在临界区内调用 `TransactionRunner`。这样可以避免在等待分布式锁期间提前打开数据库事务。

Spring Boot 运行时装配、所选锁客户端集成、用户覆盖和注解配置见 [Spring Boot 运行时装配](../implementations/spring-boot.md)。精确的启动器、配置项和自动配置条件见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。

## Quarkus

Quarkus 应用选择 `jfoundry-lock-redisson-quarkus-runtime`。Quarkus 会自动发现匹配的部署模块。该运行时使用 `redisson-quarkus-33` 提供的 `RedissonClient` 产出 `DistributedLockClient` 和 `LockExecutor`。Redis 地址通过 `quarkus.redisson.single-server-config.address` 配置。Quarkus 会拦截 `@DistributedLock`，并用 Jakarta EL 求值 key。需要编程式调用时仍可注入 `LockExecutor`。

## Helidon

Helidon MP 应用选择 `jfoundry-lock-redisson-helidon`。该装配使用 `redisson-helidon-40` 提供的 `RedissonClient` 产出 `DistributedLockClient` 和 `LockExecutor`，并用 Jakarta EL 拦截 `@DistributedLock`。Redis 地址通过 `org.redisson.Redisson.default.singleServerConfig.address` 配置。该装配只支持 JVM。Helidon 原生镜像任务不包含 Redisson。

基础 Quarkus 原生镜像任务不包含 Redisson。独立的 `native-redisson` 阶段会对照 Redis 验证这把锁。
