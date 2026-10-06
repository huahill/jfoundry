package org.jfoundry.application.outbox;

import java.time.Duration;
import java.util.Objects;

/// Exponential backoff: `min(base * 2^failedAttempts, max)`.
public record ExponentialBackoffStrategy(Duration base, Duration max) implements BackoffStrategy {

    public ExponentialBackoffStrategy {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(max, "max");
        if (base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("base must be positive");
        }
        if (max.compareTo(base) < 0) {
            throw new IllegalArgumentException("max must not be less than base");
        }
    }

    @Override
    public Duration nextDelay(int failedAttempts) {
        int exponent = Math.max(0, failedAttempts);
        long baseMs = base.toMillis();
        long maxMs = max.toMillis();
        long computed;
        try {
            computed = Math.multiplyExact(baseMs, 1L << exponent);
        } catch (ArithmeticException overflow) {
            computed = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(computed, maxMs));
    }
}
