package org.jfoundry.integration.nativeimage.redisson;

import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/// HTTP operation that verifies the JFoundry lock abstraction against a real Redis server.
@RestController
class NativeRedissonLockResource {

    private final LockExecutor lockExecutor;

    NativeRedissonLockResource(LockExecutor lockExecutor) {
        this.lockExecutor = lockExecutor;
    }

    @GetMapping("/jfoundry/native/redisson/lock")
    NativeRedissonLockResult acquireAndReleaseLock() throws Exception {
        boolean locked = lockExecutor.execute(
                new LockKey("native-redisson", "verification"),
                LockOptions.builder().leaseTime(Duration.ofSeconds(5)).build(),
                () -> true);
        return new NativeRedissonLockResult(locked);
    }
}
