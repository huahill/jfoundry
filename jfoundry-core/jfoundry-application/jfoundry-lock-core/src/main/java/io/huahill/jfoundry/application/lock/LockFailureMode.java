package io.huahill.jfoundry.application.lock;

/**
 * Defines how lock acquisition failures are surfaced to the caller.
 */
public enum LockFailureMode {

    /**
     * Throw {@link DistributedLockUnavailableException} when the lock cannot be acquired.
     */
    THROW,

    /**
     * Skip the callback and return {@code null} when the lock cannot be acquired.
     */
    SKIP
}
