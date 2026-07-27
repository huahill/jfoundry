package org.jfoundry.application.lock;

import java.util.Objects;

/// Executes callbacks while holding a distributed lock.
public interface LockExecutor {

    /// Creates the default executor backed by the supplied lock client.
    static LockExecutor create(DistributedLockClient lockClient) {
        return new DefaultLockExecutor(lockClient);
    }

    /// Executes the callback while the key is held according to the supplied options.
    <T> T execute(LockKey key, LockOptions options, LockCallback<T> callback) throws Exception;
}

final class DefaultLockExecutor implements LockExecutor {

    private final DistributedLockClient lockClient;

    DefaultLockExecutor(DistributedLockClient lockClient) {
        this.lockClient = Objects.requireNonNull(lockClient, "lockClient must not be null");
    }

    @Override
    public <T> T execute(LockKey key, LockOptions options, LockCallback<T> callback) throws Exception {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        LockHandle handle = lockClient.tryLock(key, options);
        if (!handle.acquired()) {
            if (options.failureMode() == LockFailureMode.SKIP) {
                return null;
            }
            throw new DistributedLockUnavailableException(key);
        }
        try {
            return callback.execute();
        } finally {
            handle.release();
        }
    }
}
