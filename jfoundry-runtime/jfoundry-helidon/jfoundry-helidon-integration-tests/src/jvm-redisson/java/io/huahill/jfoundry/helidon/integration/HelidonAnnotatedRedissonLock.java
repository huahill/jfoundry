package io.huahill.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import io.huahill.jfoundry.application.lock.DistributedLock;

/// Separate bean so the distributed-lock annotation is invoked through a CDI proxy.
@ApplicationScoped
public class HelidonAnnotatedRedissonLock {

    @DistributedLock(key = "order:#{orderId}", waitTime = "2s", leaseTime = "5s")
    public boolean acquire(String orderId) {
        return orderId != null && !orderId.isBlank();
    }
}
