package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;

/// Periodically cleans Outbox records that are in terminal states (PUBLISHED / DEAD_LETTERED)
/// and exceed their retention period.
/// <p>
/// Scenario: accumulated PUBLISHED / DEAD_LETTERED records in the Outbox table can slow down
/// claim/dispatch queries. This job periodically cleans them according to
/// {@link OutboxCleanupProperties#getPublishedRetentionDays()} and
/// {@link OutboxCleanupProperties#getDeadLetteredRetentionDays()}.
/// <p>
/// Scheduling: {@code @Scheduled(fixedDelayString = "${jfoundry.outbox.cleanup.interval:86400000}")},
/// defaulting to 24h (86400000ms). {@code jfoundry.outbox.cleanup.interval} accepts Spring Boot
/// Duration strings such as {@code 24h}, {@code PT24H}, or {@code 86400000}, parsed by
/// {@link OutboxCleanupProperties#getInterval()}. The resolved placeholder must be rendered as
/// milliseconds; otherwise {@code @Scheduled} long parsing throws {@link IllegalArgumentException},
/// for example when Spring 6.x tries {@code Long.parseLong("24h")}.
/// <p>
/// The job is idempotent. Repeated execution has no side effects, and failures do not affect the
/// main Outbox path because claim/dispatch does not depend on deleted terminal records.
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxMessageStore outboxRepository;
    private final OutboxCleanupProperties properties;
    private final TransactionRunner transactionRunner;

    @Autowired
    public OutboxCleanupJob(OutboxMessageStore outboxRepository, OutboxCleanupProperties properties) {
        this(outboxRepository, properties, null);
    }

    public OutboxCleanupJob(OutboxMessageStore outboxRepository,
                            OutboxCleanupProperties properties,
                            TransactionRunner transactionRunner) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.transactionRunner = transactionRunner;
    }

    /// Executes one cleanup round and returns the total number of deleted records
    /// (PUBLISHED + DEAD_LETTERED), supporting tests and operational monitoring.
    /// <p>
    /// When {@link OutboxCleanupProperties#getEnabled()} is {@code false}, returns 0 without touching
    /// the repository. This allows applications to fully disable deletion through
    /// {@code jfoundry.outbox.cleanup.enabled=false}, for example on read-only replicas.
    @Scheduled(fixedDelayString = "${jfoundry.outbox.cleanup.interval:86400000}")
    public int runOnce() {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return 0;
        }

        Instant now = Instant.now();
        Instant publishedCutoff = now.minus(Duration.ofDays(properties.getPublishedRetentionDays()));
        Instant deadCutoff = now.minus(Duration.ofDays(properties.getDeadLetteredRetentionDays()));

        int publishedDeleted = inNewTransaction(() -> outboxRepository.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, publishedCutoff, properties.getBatchSize()));
        int deadDeleted = inNewTransaction(() -> outboxRepository.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.DEAD_LETTERED, deadCutoff, properties.getBatchSize()));

        int total = publishedDeleted + deadDeleted;
        if (total > 0) {
            log.info("Outbox cleanup: deleted {} PUBLISHED (retention {}d), {} DEAD_LETTERED (retention {}d)",
                    publishedDeleted, properties.getPublishedRetentionDays(),
                    deadDeleted, properties.getDeadLetteredRetentionDays());
        }
        return total;
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        if (transactionRunner == null) {
            try {
                return callback.execute();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Outbox cleanup failed", exception);
            }
        }
        try {
            return transactionRunner.call(TransactionOptions.builder()
                    .name("jfoundry-outbox-cleanup")
                    .propagation(TransactionPropagation.REQUIRES_NEW)
                    .build(), callback);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox cleanup transaction failed", exception);
        }
    }
}
