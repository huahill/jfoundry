package org.jfoundry.infrastructure.lock.redisson;

import org.jfoundry.application.lock.DistributedLockClient;
import org.jfoundry.application.lock.LockHandle;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Redisson {@link RLock}-based implementation of {@link DistributedLockClient}.
public class RedissonDistributedLockClient implements DistributedLockClient {

    private final RedissonClient redissonClient;

    public RedissonDistributedLockClient(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public LockHandle tryLock(LockKey key, LockOptions options) throws Exception {
        Objects.requireNonNull(options, "options must not be null");
        RLock lock = redissonClient.getLock(key.backendName());
        boolean acquired = tryAcquire(lock, options);
        return new LockHandle(key, acquired, () -> unlock(lock));
    }

    private static boolean tryAcquire(RLock lock, LockOptions options) throws InterruptedException {
        long waitMillis = options.waitTime().map(Duration::toMillis).orElse(0L);
        if (options.leaseTime().isPresent()) {
            return lock.tryLock(waitMillis, options.leaseTime().orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
        }
        return lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
    }

    private static void unlock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
