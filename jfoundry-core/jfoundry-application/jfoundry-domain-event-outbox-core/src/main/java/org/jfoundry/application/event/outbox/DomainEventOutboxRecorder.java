package org.jfoundry.application.event.outbox;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Optional adapter that records captured domain events as Outbox rows.
/// <p>
/// Domain events and Outbox are independent primitives. This contract is the
/// composition between them. Outbox persistence does not require it; use
/// {@link OutboxTemplate} for integration messages that are not derived from
/// domain events.
public interface DomainEventOutboxRecorder {

    void record(List<? extends DomainEvent> events);
}
