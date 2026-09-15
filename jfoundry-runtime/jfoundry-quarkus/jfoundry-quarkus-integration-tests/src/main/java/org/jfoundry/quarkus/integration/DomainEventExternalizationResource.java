package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.externalization.AggregateRouting;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.annotation.Externalized;

import java.util.List;

@Path("/jfoundry/domain-event-externalization")
@ApplicationScoped
public class DomainEventExternalizationResource {

    private final ExternalizingApplicationService applicationService;
    private final TransactionRunner transactionRunner;
    private final EntityManager entityManager;

    @Inject
    public DomainEventExternalizationResource(
            ExternalizingApplicationService applicationService,
            TransactionRunner transactionRunner,
            EntityManager entityManager) {
        this.applicationService = applicationService;
        this.transactionRunner = transactionRunner;
        this.entityManager = entityManager;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String externalize() throws Exception {
        String eventId = applicationService.handle();
        return transactionRunner.call(() -> {
            List<?> rows = entityManager.createNativeQuery("""
                    select topic, payload_key, status, payload_json
                    from jfoundry_outbox_event
                    where event_id = ?1
                    """)
                    .setParameter(1, eventId)
                    .getResultList();
            if (rows.isEmpty()) {
                return "MISSING";
            }
            Object[] row = (Object[]) rows.getFirst();
            String topic = String.valueOf(row[0]);
            String payloadKey = String.valueOf(row[1]);
            String status = String.valueOf(row[2]);
            String payloadJson = row[3] == null ? "" : String.valueOf(row[3]);
            return topic + ":" + payloadKey + ":" + status + ":"
                    + payloadJson.contains("\"eventId\"") + ":"
                    + payloadJson.contains("\"occurredAt\"");
        });
    }

    @ApplicationScoped
    @ApplicationService
    static class ExternalizingApplicationService {

        private final DomainEventContext domainEventContext;

        @Inject
        ExternalizingApplicationService(DomainEventContext domainEventContext) {
            this.domainEventContext = domainEventContext;
        }

        @Transactional
        String handle() {
            ExternalizedOrderCreated event = new ExternalizedOrderCreated();
            domainEventContext.register(OrderAggregate.created(new OrderId("order-1"), event));
            return event.getEventId().toString();
        }
    }

    static final class OrderAggregate extends BaseAggregateRoot<OrderAggregate, OrderId> {

        private OrderAggregate(OrderId id) {
            super(id);
        }

        private static OrderAggregate created(OrderId id, ExternalizedOrderCreated event) {
            OrderAggregate aggregate = new OrderAggregate(id);
            aggregate.recordEvent(event);
            return aggregate;
        }
    }

    record OrderId(String value) implements Identifier {
    }

    @Externalized("orders.created")
    @AggregateRouting(type = "order", id = "orderId")
    static final class ExternalizedOrderCreated extends BaseDomainEvent {

        public String getOrderId() {
            return "order-1";
        }
    }
}
