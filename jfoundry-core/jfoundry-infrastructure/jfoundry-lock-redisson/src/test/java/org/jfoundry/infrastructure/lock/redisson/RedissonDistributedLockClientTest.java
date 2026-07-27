package org.jfoundry.infrastructure.lock.redisson;

import org.jfoundry.application.lock.LockHandle;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonDistributedLockClientTest {

    @Test
    void acquiresTimedRedissonLockAndReleasesWhenHeldByCurrentThread() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        LockKey key = new LockKey("order-processing", "order:1");
        when(redissonClient.getLock(key.backendName())).thenReturn(lock);
        when(lock.tryLock(2000, 10000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonDistributedLockClient client = new RedissonDistributedLockClient(redissonClient);
        LockOptions options = LockOptions.builder()
                .waitTime(Duration.ofSeconds(2))
                .leaseTime(Duration.ofSeconds(10))
                .build();

        LockHandle handle = client.tryLock(key, options);
        handle.release();

        assertThat(handle.acquired()).isTrue();
        verify(lock).tryLock(2000, 10000, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }
}
