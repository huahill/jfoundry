package org.jfoundry.application.lock;

import java.util.Objects;

/// Result of a distributed lock acquisition attempt.
public record LockHandle(LockKey key, boolean acquired, Runnable releaseAction) {

    public LockHandle {
        key = Objects.requireNonNull(key, "key must not be null");
        releaseAction = Objects.requireNonNull(releaseAction, "releaseAction must not be null");
    }

    public LockHandle(LockKey key, boolean acquired) {
        this(key, acquired, () -> {
        });
    }

    public void release() {
        if (acquired) {
            releaseAction.run();
        }
    }
}
