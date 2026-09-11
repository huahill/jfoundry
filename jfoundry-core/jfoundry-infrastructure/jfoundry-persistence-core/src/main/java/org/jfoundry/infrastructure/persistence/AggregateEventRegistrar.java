package org.jfoundry.infrastructure.persistence;

import org.jfoundry.domain.event.EventRecordable;

/// Registers a successfully persisted aggregate for later domain-event collection.
/// <p>
/// Runtime integrations bind this callback to their domain-event context. Persistence
/// adapters must not depend on that context type.
@FunctionalInterface
public interface AggregateEventRegistrar {

    /// Records one aggregate after a successful complete persistence operation.
    void register(EventRecordable aggregate);
}
