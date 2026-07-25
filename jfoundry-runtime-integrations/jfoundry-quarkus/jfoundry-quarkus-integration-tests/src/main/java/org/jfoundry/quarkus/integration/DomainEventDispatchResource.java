package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;

@Path("/jfoundry/domain-events")
@ApplicationScoped
public class DomainEventDispatchResource {

    private final DomainEventApplicationService applicationService;
    private final RecordingDomainEventDispatcher dispatcher;
    private final RecordingCdiObserver cdiObserver;
    private final TransactionRunner transactionRunner;

    @Inject
    public DomainEventDispatchResource(
            DomainEventApplicationService applicationService,
            RecordingDomainEventDispatcher dispatcher,
            RecordingCdiObserver cdiObserver,
            TransactionRunner transactionRunner) {
        this.applicationService = applicationService;
        this.dispatcher = dispatcher;
        this.cdiObserver = cdiObserver;
        this.transactionRunner = transactionRunner;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String dispatchNestedEvents() {
        dispatcher.reset();
        applicationService.handle();
        return dispatcher.dispatchedAggregateIds().toString();
    }

    @GET
    @Path("/failure")
    @Produces(MediaType.TEXT_PLAIN)
    public String discardEventsWhenApplicationServiceFails() {
        dispatcher.reset();
        try {
            applicationService.handleAndFail();
        } catch (IllegalStateException exception) {
            return dispatcher.dispatchedAggregateIds().toString();
        }
        throw new IllegalStateException("Expected application service to fail");
    }

    @GET
    @Path("/cdi/transaction")
    @Produces(MediaType.TEXT_PLAIN)
    public String publishesCdiEventsAfterCommit() throws Exception {
        cdiObserver.reset();
        transactionRunner.run(applicationService::handle);
        return cdiObserver.observedAggregateIds().toString();
    }

    @ApplicationScoped
    @ApplicationService
    static class DomainEventApplicationService {

        private final DomainEventContext domainEventContext;
        private final NestedDomainEventApplicationService nestedApplicationService;

        @Inject
        DomainEventApplicationService(
                DomainEventContext domainEventContext,
                NestedDomainEventApplicationService nestedApplicationService) {
            this.domainEventContext = domainEventContext;
            this.nestedApplicationService = nestedApplicationService;
        }

        void handle() {
            domainEventContext.register(new EventAggregate("outer"));
            nestedApplicationService.handle();
        }

        void handleAndFail() {
            domainEventContext.register(new EventAggregate("failed"));
            throw new IllegalStateException("failure");
        }
    }

    @ApplicationScoped
    @ApplicationService
    static class NestedDomainEventApplicationService {

        private final DomainEventContext domainEventContext;

        @Inject
        NestedDomainEventApplicationService(DomainEventContext domainEventContext) {
            this.domainEventContext = domainEventContext;
        }

        void handle() {
            domainEventContext.register(new EventAggregate("inner"));
        }
    }

    @ApplicationScoped
    static class RecordingDomainEventDispatcher implements DomainEventDispatcher {

        private final List<String> aggregateIds = new ArrayList<>();

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            events.stream()
                    .filter(RecordedDomainEvent.class::isInstance)
                    .map(RecordedDomainEvent.class::cast)
                    .map(RecordedDomainEvent::aggregateId)
                    .forEach(aggregateIds::add);
        }

        List<String> dispatchedAggregateIds() {
            return List.copyOf(aggregateIds);
        }

        void reset() {
            aggregateIds.clear();
        }
    }

    @ApplicationScoped
    static class RecordingCdiObserver {

        private final List<String> aggregateIds = new ArrayList<>();

        void observe(@Observes RecordedDomainEvent event) {
            aggregateIds.add(event.aggregateId());
        }

        List<String> observedAggregateIds() {
            return List.copyOf(aggregateIds);
        }

        void reset() {
            aggregateIds.clear();
        }
    }

    private static final class EventAggregate implements EventRecordable {

        private List<DomainEvent> events;

        private EventAggregate(String aggregateId) {
            events = List.of(new RecordedDomainEvent(aggregateId));
        }

        @Override
        public List<DomainEvent> drainEvents() {
            List<DomainEvent> drained = events;
            events = List.of();
            return drained;
        }
    }

    private record RecordedDomainEvent(String aggregateId) implements DomainEvent {
    }
}
