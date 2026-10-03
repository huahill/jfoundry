package org.jfoundry.quarkus.integration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;

import java.time.Instant;

/// Records one Outbox row for the JobRunr dispatch acceptance application.
@ApplicationScoped
public class QuarkusJobRunrOutboxSeed {

    static final String EVENT_ID = "quarkus-jobrunr-event";

    private final TransactionRunner transactionRunner;
    private final OutboxMessageStore outboxMessageStore;

    @Inject
    public QuarkusJobRunrOutboxSeed(TransactionRunner transactionRunner, OutboxMessageStore outboxMessageStore) {
        this.transactionRunner = transactionRunner;
        this.outboxMessageStore = outboxMessageStore;
    }

    void seed(@Observes StartupEvent event) {
        transactionRunner.run(() -> outboxMessageStore.append(OutboxMessage.newPending(
                EVENT_ID,
                "orders",
                EVENT_ID,
                "order.created.v1",
                "{}",
                Instant.now())));
    }
}
