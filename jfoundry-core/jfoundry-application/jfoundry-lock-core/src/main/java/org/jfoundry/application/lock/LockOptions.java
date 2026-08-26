package org.jfoundry.application.lock;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-neutral options for acquiring a distributed lock.
 */
public record LockOptions(
        Optional<Duration> waitTime,
        Optional<Duration> leaseTime,
        LockFailureMode failureMode) {

    public LockOptions {
        waitTime = Objects.requireNonNull(waitTime, "waitTime must not be null");
        leaseTime = Objects.requireNonNull(leaseTime, "leaseTime must not be null");
        waitTime.ifPresent(LockOptions::requireNonNegative);
        leaseTime.ifPresent(LockOptions::requirePositive);
        failureMode = Objects.requireNonNullElse(failureMode, LockFailureMode.THROW);
    }

    public static LockOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void requireNonNegative(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("waitTime must not be negative");
        }
    }

    private static void requirePositive(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }
    }

    public static final class Builder {

        private Duration waitTime = Duration.ZERO;
        private @Nullable Duration leaseTime;
        private LockFailureMode failureMode = LockFailureMode.THROW;

        private Builder() {
        }

        public Builder waitTime(Duration waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        public Builder leaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
            return this;
        }

        public Builder failureMode(LockFailureMode failureMode) {
            this.failureMode = failureMode;
            return this;
        }

        public LockOptions build() {
            return new LockOptions(Optional.ofNullable(waitTime), Optional.ofNullable(leaseTime), failureMode);
        }
    }
}
