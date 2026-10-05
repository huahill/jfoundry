package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.outbox.DefaultOutboxMaintenance;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/jfoundry/outbox/maintenance")
@ApplicationScoped
public class OutboxMaintenanceResource {

    private static final int CLAIM_LIMIT = 1;

    private final TransactionRunner transactionRunner;
    private final OutboxMessageStore outboxMessageStore;
    private final DefaultOutboxMaintenance outboxMaintenance;
    private final EntityManager entityManager;

    public OutboxMaintenanceResource(
            TransactionRunner transactionRunner,
            OutboxMessageStore outboxMessageStore,
            DefaultOutboxMaintenance outboxMaintenance,
            EntityManager entityManager) {
        this.transactionRunner = transactionRunner;
        this.outboxMessageStore = outboxMessageStore;
        this.outboxMaintenance = outboxMaintenance;
        this.entityManager = entityManager;
    }

    @GET
    @Path("/recover")
    @Produces(MediaType.TEXT_PLAIN)
    public String recoverStuckDispatching() {
        try {
            String eventId = UUID.randomUUID().toString();
            transactionRunner.run(() -> outboxMessageStore.append(newMessage(eventId, Instant.EPOCH)));
            transactionRunner.run(() -> requireClaimed(
                    outboxMessageStore.claimDispatchable(CLAIM_LIMIT, "maintenance-recovery"),
                    eventId,
                    "claim"));

            int recovered = outboxMaintenance.recoverStuckDispatching(Duration.ZERO);
            String status = transactionRunner.call(() -> {
                Object value = entityManager.createNativeQuery("""
                        select status from jfoundry_outbox_event where event_id = ?1
                        """)
                        .setParameter(1, eventId)
                        .getSingleResult();
                return value == null ? "MISSING" : String.valueOf(value);
            });
            transactionRunner.run(() -> {
                OutboxMessage claimed = requireClaimed(
                        outboxMessageStore.claimDispatchable(CLAIM_LIMIT, "maintenance-recovery-cleanup"),
                        eventId,
                        "reclaim");
                outboxMessageStore.markAsPublished(claimed.getEventId(), claimed.getClaimToken());
            });
            return status + ":" + recovered;
        } catch (RuntimeException exception) {
            return error(exception);
        }
    }

    @GET
    @Path("/cleanup")
    @Produces(MediaType.TEXT_PLAIN)
    public String cleanUpTerminalMessages() {
        try {
            String publishedEventId = UUID.randomUUID().toString();
            String deadLetteredEventId = UUID.randomUUID().toString();
            Instant occurredAt = Instant.now().minus(Duration.ofDays(2));
            transactionRunner.run(() -> {
                outboxMessageStore.append(newMessage(publishedEventId, occurredAt));
                outboxMessageStore.append(newMessage(deadLetteredEventId, occurredAt));
            });
            Set<String> fixtureEventIds = Set.of(publishedEventId, deadLetteredEventId);
            transactionRunner.run(() -> {
                finishCleanupClaim(fixtureEventIds, publishedEventId);
                finishCleanupClaim(fixtureEventIds, publishedEventId);
            });

            int deleted = outboxMaintenance.cleanUpTerminalMessages(0, 0, 1000);
            long remaining = transactionRunner.call(() -> ((Number) entityManager.createNativeQuery("""
                    select count(*) from jfoundry_outbox_event where event_id = ?1 or event_id = ?2
                    """)
                    .setParameter(1, publishedEventId)
                    .setParameter(2, deadLetteredEventId)
                    .getSingleResult()).longValue());
            return deleted + ":" + remaining;
        } catch (RuntimeException exception) {
            return error(exception);
        }
    }

    /// Claim order is occurrence time, then event id. The cleanup rows share one occurrence
    /// time, so either fixture id may arrive first.
    private void finishCleanupClaim(Set<String> fixtureEventIds, String publishedEventId) {
        OutboxMessage claimed = requireClaimed(
                outboxMessageStore.claimDispatchable(CLAIM_LIMIT, "maintenance-cleanup"),
                fixtureEventIds,
                "cleanup");
        if (publishedEventId.equals(claimed.getEventId())) {
            outboxMessageStore.markAsPublished(claimed.getEventId(), claimed.getClaimToken());
            return;
        }
        outboxMessageStore.markAsFailed(
                claimed.getEventId(),
                claimed.getClaimToken(),
                "test failure",
                1,
                failedAttempts -> Duration.ZERO);
    }

    private static OutboxMessage requireClaimed(List<OutboxMessage> claimed, String eventId, String step) {
        return requireClaimed(claimed, Set.of(eventId), step);
    }

    private static OutboxMessage requireClaimed(List<OutboxMessage> claimed, Set<String> eventIds, String step) {
        return claimed.stream()
                .filter(message -> eventIds.contains(message.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        step + " claimed no rows for " + eventIds + "; claimed="
                                + claimed.stream().map(OutboxMessage::getEventId).toList()));
    }

    /// Native Failsafe hides the cause behind problem+json. Return it in the body so the
    /// assertion shows the real exception if the fixture still fails.
    private static String error(RuntimeException exception) {
        Throwable cause = exception;
        StringBuilder text = new StringBuilder("ERROR");
        while (cause != null) {
            text.append(':').append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                text.append(':').append(cause.getMessage().replace('\n', ' '));
            }
            cause = cause.getCause();
        }
        return text.toString();
    }

    private static OutboxMessage newMessage(String eventId, Instant occurredAt) {
        return OutboxMessage.newPending(eventId, "orders", eventId, "order.created.v1", "{}", occurredAt);
    }
}
