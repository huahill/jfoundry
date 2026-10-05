package org.jfoundry.application.outbox;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/// Intervals and batch sizes for {@link JdkOutboxWorker}.
///
/// Recovery and cleanup always run when the worker is running. The only process-level
/// switch is {@code jfoundry.outbox.dispatcher.enabled}.
public record JdkOutboxWorkerSettings(
        int dispatchBatchSize,
        Duration dispatchInterval,
        Duration recoveryInterval,
        Duration recoveryStuckTimeout,
        Duration cleanupInterval,
        int publishedRetentionDays,
        int deadLetteredRetentionDays,
        int cleanupBatchSize) {

    public JdkOutboxWorkerSettings {
        if (dispatchBatchSize <= 0) {
            throw new IllegalArgumentException("dispatchBatchSize must be positive");
        }
        Objects.requireNonNull(dispatchInterval, "dispatchInterval");
        Objects.requireNonNull(recoveryInterval, "recoveryInterval");
        Objects.requireNonNull(recoveryStuckTimeout, "recoveryStuckTimeout");
        Objects.requireNonNull(cleanupInterval, "cleanupInterval");
        if (dispatchInterval.isNegative() || dispatchInterval.isZero()) {
            throw new IllegalArgumentException("dispatchInterval must be positive");
        }
        if (recoveryInterval.isNegative() || recoveryInterval.isZero()) {
            throw new IllegalArgumentException("recoveryInterval must be positive");
        }
        if (cleanupInterval.isNegative() || cleanupInterval.isZero()) {
            throw new IllegalArgumentException("cleanupInterval must be positive");
        }
        if (recoveryStuckTimeout.isNegative()) {
            throw new IllegalArgumentException("recoveryStuckTimeout must not be negative");
        }
        if (publishedRetentionDays < 0 || deadLetteredRetentionDays < 0 || cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("Invalid Outbox cleanup configuration");
        }
    }

    /// Reads optional configuration values, typically MicroProfile {@code Config#getOptionalValue}.
    @FunctionalInterface
    public interface ValueLookup {
        <T> Optional<T> get(String name, Class<T> type);
    }

    public static JdkOutboxWorkerSettings defaults() {
        return new JdkOutboxWorkerSettings(
                50,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Duration.ofHours(24),
                7,
                30,
                1000);
    }

    public static boolean workerEnabled(ValueLookup values) {
        Objects.requireNonNull(values, "values");
        return values.get("jfoundry.outbox.dispatcher.enabled", Boolean.class).orElse(true);
    }

    public static JdkOutboxWorkerSettings from(ValueLookup values) {
        Objects.requireNonNull(values, "values");
        JdkOutboxWorkerSettings defaults = defaults();
        return new JdkOutboxWorkerSettings(
                values.get("jfoundry.outbox.dispatcher.batch-size", Integer.class).orElse(defaults.dispatchBatchSize()),
                values.get("jfoundry.outbox.dispatcher.interval", Duration.class).orElse(defaults.dispatchInterval()),
                values.get("jfoundry.outbox.recovery.interval", Duration.class).orElse(defaults.recoveryInterval()),
                values.get("jfoundry.outbox.recovery.stuck-timeout", Duration.class).orElse(defaults.recoveryStuckTimeout()),
                values.get("jfoundry.outbox.cleanup.interval", Duration.class).orElse(defaults.cleanupInterval()),
                values.get("jfoundry.outbox.cleanup.published-retention-days", Integer.class).orElse(defaults.publishedRetentionDays()),
                values.get("jfoundry.outbox.cleanup.dead-lettered-retention-days", Integer.class).orElse(defaults.deadLetteredRetentionDays()),
                values.get("jfoundry.outbox.cleanup.batch-size", Integer.class).orElse(defaults.cleanupBatchSize()));
    }
}
