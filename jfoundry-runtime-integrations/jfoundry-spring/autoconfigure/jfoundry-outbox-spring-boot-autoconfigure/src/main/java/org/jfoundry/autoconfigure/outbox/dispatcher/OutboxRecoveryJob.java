package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.outbox.OutboxMessageStore;
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

/// Periodically recovers stuck DISPATCHING records.
/// <p>
/// Scenario: a pod may crash or be killed while rows are in DISPATCHING, leaving records stuck in
/// that state. This job rolls them back according to the
/// {@code jfoundry.outbox.recovery.stuck-timeout} threshold.
/// <p>
/// Scheduling: {@code @Scheduled(fixedDelayString = "${jfoundry.outbox.recovery.interval:60000}")},
/// defaulting to 60s (60000ms). {@code jfoundry.outbox.recovery.interval} accepts Spring Boot
/// Duration strings such as {@code 60s}, {@code PT1M}, or {@code 60000}, parsed by
/// {@link OutboxRecoveryProperties#getInterval()}. The resolved placeholder must be rendered as
/// milliseconds.
public class OutboxRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRecoveryJob.class);

    private final OutboxMessageStore outboxRepository;
    private final OutboxRecoveryProperties properties;
    private final TransactionRunner transactionRunner;

    @Autowired
    public OutboxRecoveryJob(OutboxMessageStore outboxRepository, OutboxRecoveryProperties properties) {
        this(outboxRepository, properties, null);
    }

    public OutboxRecoveryJob(OutboxMessageStore outboxRepository,
                             OutboxRecoveryProperties properties,
                             TransactionRunner transactionRunner) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.transactionRunner = transactionRunner;
    }

    /// Resets stuck DISPATCHING records and returns the recovered count for tests and monitoring.
    @Scheduled(fixedDelayString = "${jfoundry.outbox.recovery.interval:60000}")
    public int recoverStuckDispatching() {
        Duration timeout = properties.getStuckTimeout();
        Instant cutoff = Instant.now().minus(timeout);
        int recovered = inNewTransaction(() -> outboxRepository.recoverStuckDispatching(cutoff));
        if (recovered > 0) {
            log.warn("Recovered {} stuck DISPATCHING outbox records (threshold={})",
                    recovered, timeout);
        }
        return recovered;
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        if (transactionRunner == null) {
            try {
                return callback.execute();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Outbox recovery failed", exception);
            }
        }
        try {
            return transactionRunner.call(TransactionOptions.builder()
                    .name("jfoundry-outbox-recovery")
                    .propagation(TransactionPropagation.REQUIRES_NEW)
                    .build(), callback);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox recovery transaction failed", exception);
        }
    }
}
