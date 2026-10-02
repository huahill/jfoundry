package org.jfoundry.infrastructure.lock.redisson.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.application.lock.DistributedLockClient;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.infrastructure.lock.redisson.RedissonDistributedLockClient;
import org.redisson.api.RedissonClient;

/// Provides the default Redisson lock client and executor for Quarkus applications.
@ApplicationScoped
public final class QuarkusRedissonLockProducer {

    @Produces
    @DefaultBean
    @ApplicationScoped
    DistributedLockClient distributedLockClient(RedissonClient redissonClient) {
        return new RedissonDistributedLockClient(redissonClient);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    LockExecutor lockExecutor(DistributedLockClient distributedLockClient) {
        return LockExecutor.create(distributedLockClient);
    }
}
