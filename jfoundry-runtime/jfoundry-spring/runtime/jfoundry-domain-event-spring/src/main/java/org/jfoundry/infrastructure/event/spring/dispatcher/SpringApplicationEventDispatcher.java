package org.jfoundry.infrastructure.event.spring.dispatcher;

import org.jfoundry.application.event.DomainEventBatch;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Publishes domain events through Spring's application event publisher.
 */
public class SpringApplicationEventDispatcher implements DomainEventDispatcher {

    private final ApplicationEventPublisher eventPublisher;

    public SpringApplicationEventDispatcher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        publishSpringEvents(eventBatch);
    }

    private void publishSpringEvents(List<DomainEvent> events) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.forEach(eventPublisher::publishEvent);
                }
            });
            return;
        }
        events.forEach(eventPublisher::publishEvent);
    }
}
