package org.jfoundry.application.outbox;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/// Framework-neutral Outbox recovery and cleanup.
///
/// Runtime adapters decide when this service is triggered. This class owns the shared
/// recover/cleanup transitions so Spring, Quarkus, and Helidon integrations do not duplicate
/// maintenance behavior.
public final class DefaultOutboxMaintenance {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutboxMaintenance.class);

    private final Supplier<@Nullable OutboxMessageStore> repositorySupplier;
    private final TransactionRunner transactionRunner;

    public DefaultOutboxMaintenance(OutboxMessageStore repository, TransactionRunner transactionRunner) {
        this(() -> repository, transactionRunner);
    }

    public DefaultOutboxMaintenance(
            Supplier<@Nullable OutboxMessageStore> repositorySupplier,
            TransactionRunner transactionRunner) {
        this.repositorySupplier = Objects.requireNonNull(repositorySupplier, "repositorySupplier");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
    }

    /// Resets stuck `DISPATCHING` records older than `stuckTimeout` and returns the recovered count.
    public int recoverStuckDispatching(Duration stuckTimeout) {
        Objects.requireNonNull(stuckTimeout, "stuckTimeout");
        if (stuckTimeout.isNegative()) {
            throw new IllegalArgumentException("Outbox recovery stuck timeout must not be negative");
        }
        OutboxMessageStore repository = repositorySupplier.get();
        if (repository == null) {
            log.warn("Outbox recovery requires an application bean for OutboxMessageStore");
            return 0;
        }
        Instant cutoff = Instant.now().minus(stuckTimeout);
        int recovered = inNewTransaction(() -> repository.recoverStuckDispatching(cutoff));
        if (recovered > 0) {
            log.warn("Recovered {} stuck DISPATCHING outbox records (threshold={})",
                    recovered, stuckTimeout);
        }
        return recovered;
    }

    /// Deletes expired `PUBLISHED` and `DEAD_LETTERED` records and returns the total deleted count.
    public int cleanUpTerminalMessages(int publishedRetentionDays, int deadLetteredRetentionDays, int batchSize) {
        if (publishedRetentionDays < 0 || deadLetteredRetentionDays < 0 || batchSize <= 0) {
            throw new IllegalArgumentException("Invalid Outbox cleanup configuration");
        }
        OutboxMessageStore repository = repositorySupplier.get();
        if (repository == null) {
            log.warn("Outbox cleanup requires an application bean for OutboxMessageStore");
            return 0;
        }
        Instant now = Instant.now();
        int publishedDeleted = inNewTransaction(() -> repository.deleteByStatusAndOccurredAtBefore(
                        OutboxMessageStatus.PUBLISHED,
                        now.minus(Duration.ofDays(publishedRetentionDays)),
                        batchSize));
        int deadDeleted = inNewTransaction(() -> repository.deleteByStatusAndOccurredAtBefore(
                        OutboxMessageStatus.DEAD_LETTERED,
                        now.minus(Duration.ofDays(deadLetteredRetentionDays)),
                        batchSize));
        int total = publishedDeleted + deadDeleted;
        if (total > 0) {
            log.info("Outbox cleanup: deleted {} PUBLISHED (retention {}d), {} DEAD_LETTERED (retention {}d)",
                    publishedDeleted, publishedRetentionDays, deadDeleted, deadLetteredRetentionDays);
        }
        return total;
    }

    /// JTA runtimes reject {@link TransactionOptions#name()}; keep this unnamed so Quarkus
    /// and Helidon can run recovery and cleanup.
    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        return transactionRunner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build(), callback);
    }
}
