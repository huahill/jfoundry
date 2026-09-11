package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Default phase-aware coordinator for multiple domain-event dispatchers.
public class DefaultDomainEventDispatchCoordinator implements DomainEventDispatchCoordinator {

    private final List<DomainEventDispatcher> dispatchers;

    public DefaultDomainEventDispatchCoordinator(List<DomainEventDispatcher> dispatchers) {
        this.dispatchers = List.copyOf(dispatchers);
    }

    @Override
    public void dispatchBeforeCommit(List<? extends DomainEvent> events) {
        dispatch(events, true);
    }

    @Override
    public void dispatchAfterCommit(List<? extends DomainEvent> events) {
        dispatch(events, false);
    }

    @Override
    public void dispatchWithoutTransaction(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        dispatchers.forEach(dispatcher -> dispatcher.dispatch(eventBatch));
    }

    private void dispatch(List<? extends DomainEvent> events, boolean beforeCommit) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        dispatchers.stream()
                .filter(dispatcher -> (dispatcher instanceof BeforeCommitDomainEventDispatcher) == beforeCommit)
                .forEach(dispatcher -> dispatcher.dispatch(eventBatch));
    }
}
