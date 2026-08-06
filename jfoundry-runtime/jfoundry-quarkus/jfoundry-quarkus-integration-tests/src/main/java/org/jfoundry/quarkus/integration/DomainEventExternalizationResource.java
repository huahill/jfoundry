package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.externalization.AggregateRouting;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.annotation.Externalized;

import java.time.Instant;

@Path("/jfoundry/domain-event-externalization")
@ApplicationScoped
public class DomainEventExternalizationResource {

    private final ExternalizingApplicationService applicationService;
    private final OutboxMessageStore outboxMessageStore;
    private final TransactionRunner transactionRunner;

    @Inject
    public DomainEventExternalizationResource(
            ExternalizingApplicationService applicationService,
            OutboxMessageStore outboxMessageStore,
            TransactionRunner transactionRunner) {
        this.applicationService = applicationService;
        this.outboxMessageStore = outboxMessageStore;
        this.transactionRunner = transactionRunner;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String externalize() throws Exception {
        String eventId = applicationService.handle();
        return transactionRunner.call(() -> outboxMessageStore.findDispatchable(10, Instant.now()).stream()
                .filter(message -> eventId.equals(message.getEventId()))
                .findFirst()
                .map(message -> message.getTopic() + ":" + message.getPayloadKey() + ":"
                        + message.getStatus().name() + ":"
                        + message.getPayloadJson().contains("\"eventId\"") + ":"
                        + message.getPayloadJson().contains("\"occurredAt\""))
                .orElse("MISSING"));
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
