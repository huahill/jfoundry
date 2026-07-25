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
    public String persistAndRead() throws Exception {
        String eventId = UUID.randomUUID().toString();
        transactionRunner.run(() -> outboxMessageStore.append(OutboxMessage.newPending(
                eventId,
                "orders",
                eventId,
                "order.created.v1",
                "{}",
                Instant.now())));

        return transactionRunner.call(() -> outboxMessageStore.findDispatchable(10, Instant.now()).stream()
                .filter(message -> eventId.equals(message.getEventId()))
                .findFirst()
                .map(message -> message.getStatus().name())
                .orElse("MISSING"));
    }

    @GET
    @Path("/dispatch")
    @Produces(MediaType.TEXT_PLAIN)
    public String persistAndDispatch() throws Exception {
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
