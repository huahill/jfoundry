# Distributed Locks

Distributed lock support has a framework-neutral contract: `DistributedLockClient`, `LockExecutor`,
`LockKey`, `LockOptions`, and `@DistributedLock`. Use a lock only when mutual exclusion is part of the
use-case semantics; it does not replace database consistency or aggregate invariants.

Programmatic usage:

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

`LockKey` separates a non-sensitive scope from the protected resource value. The lock client receives a
stable hashed backend name; diagnostic string representations and lock-unavailable errors include the
scope but not the value. Choose a stable scope and value so independent work remains concurrent while
conflicting work is mutually exclusive. Do not put a sensitive identifier in the scope.

For `@DistributedLock`, every runtime derives the scope from the declaring class and method. The annotation
key supplies only the value. A key that contains neither `${` nor `#{` is a literal on every runtime.

Spring evaluates the key with SpEL. Write `#orderId` or `'order:' + #orderId`. The method parameter names
are available, as are `p0` and `a0`.

Quarkus and Helidon evaluate the key with Jakarta EL. Write `#{orderId}` or `order:#{orderId}`. The same
parameter names, `p0`, and `a0` are available. Do not reuse a Spring SpEL key on those runtimes, and do not
expect Spring to evaluate a Jakarta EL key.

`waitTime` controls how long a caller may wait to acquire the lock. `leaseTime` controls the lock
lifetime when the selected lock client supports an explicit lease.

When the lock cannot be acquired, the default `LockFailureMode.THROW` raises
`DistributedLockUnavailableException`. Use `failureMode = LockFailureMode.SKIP` only when skipping
the method is an intentional business outcome.

## Ordering With Transactions

When a use case needs both a distributed lock and a transaction, acquire the lock first and call
`TransactionRunner` inside its critical section. This avoids opening a database transaction while
waiting for a distributed lock.

For Spring Boot runtime assembly, selected lock-client integration, user overrides, and annotation
configuration, see [Spring Boot Runtime Assembly](../implementations/spring-boot.md). The exact
starter, property, and auto-configuration conditions are in the
[Spring Boot Auto-configuration reference](../reference/spring-boot-autoconfiguration.md).

## Quarkus

Quarkus applications select `jfoundry-lock-redisson-quarkus-runtime`. Quarkus discovers the matching
deployment module. The runtime produces `DistributedLockClient` and `LockExecutor` from the
`RedissonClient` supplied by `redisson-quarkus-33`. Set the Redis address with
`quarkus.redisson.single-server-config.address`. Quarkus intercepts `@DistributedLock` and evaluates its
key with Jakarta EL. `LockExecutor` remains available for programmatic use.

## Helidon

Helidon MP applications select `jfoundry-lock-redisson-helidon`. The assembly produces
`DistributedLockClient` and `LockExecutor` from the `RedissonClient` supplied by `redisson-helidon-40`,
and intercepts `@DistributedLock` with Jakarta EL. Set the Redis address with
`org.redisson.Redisson.default.singleServerConfig.address`. This assembly is JVM-only. The Helidon Native
Image job does not include Redisson.

The base Quarkus Native Image job does not include Redisson. The separate `native-redisson` stage
verifies the lock against Redis.
