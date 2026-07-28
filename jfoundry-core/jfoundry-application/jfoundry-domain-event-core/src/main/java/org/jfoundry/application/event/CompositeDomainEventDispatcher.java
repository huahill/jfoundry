package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/**
 * Delegates domain event dispatch to multiple dispatchers in declaration order.
 */
public class CompositeDomainEventDispatcher implements DomainEventDispatcher {

    private final List<DomainEventDispatcher> delegates;

    public CompositeDomainEventDispatcher(List<DomainEventDispatcher> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        delegates.forEach(delegate -> delegate.dispatch(eventBatch));
    }
}
