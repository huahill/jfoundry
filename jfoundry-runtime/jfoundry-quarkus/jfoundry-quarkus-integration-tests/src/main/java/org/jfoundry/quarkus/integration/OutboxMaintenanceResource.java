package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxMaintenance;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Path("/jfoundry/outbox/maintenance")
@ApplicationScoped
public class OutboxMaintenanceResource {

    private final TransactionRunner transactionRunner;
    private final OutboxMessageStore outboxMessageStore;
    private final QuarkusOutboxMaintenance outboxMaintenance;
    private final EntityManager entityManager;

    public OutboxMaintenanceResource(
            TransactionRunner transactionRunner,
            OutboxMessageStore outboxMessageStore,
            QuarkusOutboxMaintenance outboxMaintenance,
            EntityManager entityManager) {
        this.transactionRunner = transactionRunner;
        this.outboxMessageStore = outboxMessageStore;
        this.outboxMaintenance = outboxMaintenance;
        this.entityManager = entityManager;
    }

    @GET
    @Path("/recover")
    @Produces(MediaType.TEXT_PLAIN)
    public String recoverStuckDispatching() throws Exception {
        String eventId = UUID.randomUUID().toString();
        transactionRunner.run(() -> outboxMessageStore.append(newMessage(eventId, Instant.EPOCH)));
        transactionRunner.run(() -> {
            OutboxMessage claimed = outboxMessageStore.claimDispatchable(1, "maintenance-recovery").getFirst();
            if (!eventId.equals(claimed.getEventId())) {
                throw new IllegalStateException("Recovery fixture did not claim its generated event");
            }
        });

        int recovered = outboxMaintenance.recoverStuckDispatching();
        String status = transactionRunner.call(() -> outboxMessageStore.findDispatchable(10, Instant.now()).stream()
                .filter(message -> eventId.equals(message.getEventId()))
                .findFirst()
                .map(message -> message.getStatus().name())
                .orElse("MISSING"));
        transactionRunner.run(() -> {
            OutboxMessage claimed = outboxMessageStore.claimDispatchable(1, "maintenance-recovery-cleanup").getFirst();
            if (!eventId.equals(claimed.getEventId())) {
                throw new IllegalStateException("Recovery fixture did not reclaim its generated event");
            }
            outboxMessageStore.markAsPublished(claimed.getEventId(), claimed.getClaimToken());
        });
        return status + ":" + recovered;
    }

    @GET
    @Path("/cleanup")
    @Produces(MediaType.TEXT_PLAIN)
    public String cleanUpTerminalMessages() throws Exception {
        String publishedEventId = UUID.randomUUID().toString();
        String deadLetteredEventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now().minus(Duration.ofDays(2));
        transactionRunner.run(() -> {
            outboxMessageStore.append(newMessage(publishedEventId, occurredAt));
            outboxMessageStore.append(newMessage(deadLetteredEventId, occurredAt));
        });
        transactionRunner.run(() -> {
            OutboxMessage published = outboxMessageStore.claimDispatchable(1, "maintenance-cleanup").getFirst();
            outboxMessageStore.markAsPublished(published.getEventId(), published.getClaimToken());
            OutboxMessage deadLettered = outboxMessageStore.claimDispatchable(1, "maintenance-cleanup").getFirst();
            outboxMessageStore.markAsFailed(
                    deadLettered.getEventId(),
                    deadLettered.getClaimToken(),
                    "test failure",
                    1,
                    failedAttempts -> Duration.ZERO);
        });

        int deleted = outboxMaintenance.cleanUpTerminalMessages();
        long remaining = transactionRunner.call(() -> ((Number) entityManager.createNativeQuery("""
                select count(*) from jfoundry_outbox_event where event_id = ?1 or event_id = ?2
                """)
                .setParameter(1, publishedEventId)
                .setParameter(2, deadLetteredEventId)
                .getSingleResult()).longValue());
        return deleted + ":" + remaining;
    }

    private static OutboxMessage newMessage(String eventId, Instant occurredAt) {
        return OutboxMessage.newPending(eventId, "orders", eventId, "order.created.v1", "{}", occurredAt);
    }
}
