package org.jfoundry.application.lock;

/// Framework-neutral SPI for acquiring distributed locks.
@FunctionalInterface
public interface DistributedLockClient {

    /// Attempts to acquire a lock.
    ///
    /// @param key structured lock key
    /// @param options acquisition options
    /// @return lock handle with acquisition state and release callback
    /// @throws Exception if the backend fails while acquiring the lock
    LockHandle tryLock(LockKey key, LockOptions options) throws Exception;
}
