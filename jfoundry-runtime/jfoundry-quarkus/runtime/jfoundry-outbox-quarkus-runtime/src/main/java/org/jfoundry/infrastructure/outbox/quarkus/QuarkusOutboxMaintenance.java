package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/// Quarkus scheduled maintenance for recovering and cleaning Outbox messages.
@ApplicationScoped
public class QuarkusOutboxMaintenance {

    private static final Logger log = LoggerFactory.getLogger(QuarkusOutboxMaintenance.class);

    @Inject
    Instance<OutboxMessageStore> outboxMessageStore;

    @Inject
    TransactionRunner transactionRunner;

    @ConfigProperty(name = "jfoundry.outbox.recovery.enabled", defaultValue = "false")
    boolean recoveryEnabled;

    @ConfigProperty(name = "jfoundry.outbox.recovery.stuck-timeout", defaultValue = "5m")
    Duration recoveryStuckTimeout;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.enabled", defaultValue = "false")
    boolean cleanupEnabled;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.published-retention-days", defaultValue = "7")
    int publishedRetentionDays;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.dead-lettered-retention-days", defaultValue = "30")
    int deadLetteredRetentionDays;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.batch-size", defaultValue = "1000")
    int cleanupBatchSize;

    @Scheduled(every = "${jfoundry.outbox.recovery.interval:60s}", identity = "jfoundry-outbox-recovery")
    void scheduledRecovery() {
        if (recoveryEnabled) {
            recoverStuckDispatching();
        }
    }

    @Scheduled(every = "${jfoundry.outbox.cleanup.interval:24h}", identity = "jfoundry-outbox-cleanup")
    void scheduledCleanup() {
        if (cleanupEnabled) {
            cleanUpTerminalMessages();
        }
    }

    /// Recovers stale `DISPATCHING` records in a new transaction.
    public int recoverStuckDispatching() {
        if (!outboxMessageStore.isResolvable()) {
            log.warn("Outbox recovery requires an application bean for OutboxMessageStore");
            return 0;
        }
        if (recoveryStuckTimeout.isNegative()) {
            throw new IllegalStateException("Outbox recovery stuck timeout must not be negative");
        }

        int recovered = inNewTransaction(() -> outboxMessageStore.get()
                .recoverStuckDispatching(Instant.now().minus(recoveryStuckTimeout)));
        if (recovered > 0) {
            log.warn("Recovered {} stuck DISPATCHING outbox records (threshold={})",
                    recovered, recoveryStuckTimeout);
        }
        return recovered;
    }

    /// Deletes expired `PUBLISHED` and `DEAD_LETTERED` records in separate new transactions.
    public int cleanUpTerminalMessages() {
        if (!outboxMessageStore.isResolvable()) {
            log.warn("Outbox cleanup requires an application bean for OutboxMessageStore");
            return 0;
        }
        validateCleanupConfiguration();

        Instant now = Instant.now();
        int publishedDeleted = deleteExpired(
                OutboxMessageStatus.PUBLISHED, now.minus(Duration.ofDays(publishedRetentionDays)));
        int deadLetteredDeleted = deleteExpired(
                OutboxMessageStatus.DEAD_LETTERED, now.minus(Duration.ofDays(deadLetteredRetentionDays)));
        int total = publishedDeleted + deadLetteredDeleted;
        if (total > 0) {
            log.info("Outbox cleanup: deleted {} PUBLISHED (retention {}d), {} DEAD_LETTERED (retention {}d)",
                    publishedDeleted, publishedRetentionDays, deadLetteredDeleted, deadLetteredRetentionDays);
        }
        return total;
    }

    private int deleteExpired(OutboxMessageStatus status, Instant cutoff) {
        return inNewTransaction(() -> outboxMessageStore.get()
                .deleteByStatusAndOccurredAtBefore(status, cutoff, cleanupBatchSize));
    }

    private void validateCleanupConfiguration() {
        if (publishedRetentionDays < 0 || deadLetteredRetentionDays < 0 || cleanupBatchSize <= 0) {
            throw new IllegalStateException("Invalid Outbox cleanup configuration");
        }
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        try {
            return transactionRunner.call(TransactionOptions.builder()
                    .propagation(TransactionPropagation.REQUIRES_NEW)
                    .build(), callback);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox maintenance transaction failed", exception);
        }
    }
}
