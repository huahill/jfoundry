package org.jfoundry.infrastructure.outbox.spring.backoff;

import org.jfoundry.application.outbox.BackoffStrategy;

import java.time.Duration;

/// Exponential backoff strategy: delay = min(base * 2^failedAttempts, max).
/// <p>
/// Default sequence with base=1000ms and max=5min: 1s, 2s, 4s, 8s, 16s, 32s, 60s at the cap,
/// then 60s, and so on.
public record ExponentialBackoffStrategy(long baseMs, long maxMs) implements BackoffStrategy {

    public ExponentialBackoffStrategy {
        if (baseMs <= 0) {
            throw new IllegalArgumentException("baseMs must be positive: " + baseMs);
        }
        if (maxMs < baseMs) {
            throw new IllegalArgumentException("maxMs must not be less than baseMs: maxMs="
                    + maxMs + ", baseMs=" + baseMs);
        }
    }

    @Override
    public Duration nextDelay(int failedAttempts) {
        int exponent = Math.max(0, failedAttempts);
        long computed;
        try {
            computed = Math.multiplyExact(baseMs, 1L << exponent);
        } catch (ArithmeticException overflow) {
            computed = Long.MAX_VALUE;
        }
        long delayMs = Math.min(computed, maxMs);
        return Duration.ofMillis(delayMs);
    }
}
