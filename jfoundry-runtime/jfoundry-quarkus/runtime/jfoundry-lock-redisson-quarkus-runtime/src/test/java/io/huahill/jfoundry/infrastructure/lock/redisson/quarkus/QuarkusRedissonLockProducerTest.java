package io.huahill.jfoundry.infrastructure.lock.redisson.quarkus;

import io.huahill.jfoundry.application.lock.DistributedLockClient;
import io.huahill.jfoundry.application.lock.LockExecutor;
import io.huahill.jfoundry.infrastructure.lock.redisson.RedissonDistributedLockClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuarkusRedissonLockProducerTest {

    @Test
    void createsTheRedissonLockClientAndExecutor() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        QuarkusRedissonLockProducer producer = new QuarkusRedissonLockProducer();

        DistributedLockClient client = producer.distributedLockClient(redissonClient);
        LockExecutor executor = producer.lockExecutor(client);

        assertThat(client).isInstanceOf(RedissonDistributedLockClient.class);
        assertThat(executor).isNotNull();
    }
}
