package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;

import java.time.Instant;
import java.util.UUID;

@Path("/jfoundry/outbox")
@ApplicationScoped
public class OutboxJpaResource {

    private final TransactionRunner transactionRunner;
    private final OutboxMessageStore outboxMessageStore;
    /// Keeps the integration endpoint wired to the application OutboxDispatcher port.
    private final OutboxDispatcher outboxDispatcher;
    private final EntityManager entityManager;

    public OutboxJpaResource(
            TransactionRunner transactionRunner,
            OutboxMessageStore outboxMessageStore,
            OutboxDispatcher outboxDispatcher,
            EntityManager entityManager) {
        this.transactionRunner = transactionRunner;
        this.outboxMessageStore = outboxMessageStore;
        this.outboxDispatcher = outboxDispatcher;
        this.entityManager = entityManager;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String persistAndRead() {
        String eventId = UUID.randomUUID().toString();
        transactionRunner.run(() -> outboxMessageStore.append(OutboxMessage.newPending(
                eventId,
                "orders",
                eventId,
                "order.created.v1",
                "{}",
                Instant.now())));

        return transactionRunner.call(() -> {
            Object status = entityManager.createNativeQuery("""
                    select status from jfoundry_outbox_event where event_id = ?1
                    """)
                    .setParameter(1, eventId)
                    .getSingleResult();
            return status == null ? "MISSING" : String.valueOf(status);
        });
    }

    @GET
    @Path("/dispatch")
    @Produces(MediaType.TEXT_PLAIN)
    public String persistAndDispatch() {
        String eventId = UUID.randomUUID().toString();
        transactionRunner.run(() -> outboxMessageStore.append(OutboxMessage.newPending(
                eventId,
                "orders",
                eventId,
                "order.created.v1",
                "{}",
                Instant.now())));
        outboxDispatcher.dispatch(10);
        return transactionRunner.call(() -> String.valueOf(entityManager.createNativeQuery("""
                select status from jfoundry_outbox_event where event_id = ?1
                """)
                .setParameter(1, eventId)
                .getSingleResult()));
    }
}
