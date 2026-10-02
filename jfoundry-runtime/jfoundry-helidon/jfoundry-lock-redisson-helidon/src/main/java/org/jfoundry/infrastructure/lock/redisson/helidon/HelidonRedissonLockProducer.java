package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.application.lock.DistributedLockClient;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.infrastructure.lock.redisson.RedissonDistributedLockClient;
import org.redisson.api.RedissonClient;

/// Provides the Redisson lock client and executor for Helidon MP applications.
@ApplicationScoped
public class HelidonRedissonLockProducer {

    @Produces
    @ApplicationScoped
    DistributedLockClient distributedLockClient(RedissonClient redissonClient) {
        return new RedissonDistributedLockClient(redissonClient);
    }

    @Produces
    @ApplicationScoped
    LockExecutor lockExecutor(DistributedLockClient distributedLockClient) {
        return LockExecutor.create(distributedLockClient);
    }
}
