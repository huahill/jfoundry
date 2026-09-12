package org.jfoundry.infrastructure.persistence.event;

import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserver;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jfoundry.domain.event.EventRecordable;

import java.util.Objects;

/// Adapts successful aggregate persistence to the application Domain Event context.
/// <p>
/// The adapter ignores aggregates that do not implement {@link EventRecordable}, allowing one
/// persistence configuration to serve both event-recording and ordinary aggregates.
public final class DomainEventAggregatePersistenceObserver implements AggregatePersistenceObserver {

    private final DomainEventContext domainEventContext;

    public DomainEventAggregatePersistenceObserver(DomainEventContext domainEventContext) {
        this.domainEventContext = Objects.requireNonNull(
                domainEventContext, "DomainEventContext must not be null.");
    }

    @Override
    public void afterPersisted(AggregateRoot<?, ?> aggregate) {
        if (aggregate instanceof EventRecordable eventRecordable) {
            domainEventContext.register(eventRecordable);
        }
    }
}
