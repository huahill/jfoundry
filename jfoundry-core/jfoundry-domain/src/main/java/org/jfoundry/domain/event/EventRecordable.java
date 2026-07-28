package org.jfoundry.domain.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Capability for recording domain events.
/// <p>
/// Represents domain events explicitly produced by an aggregate root inside its
/// own business boundary. Automatic runtime dispatchers extract them after a
/// successful application-service boundary; manual runtime hosts can extract
/// and dispatch them directly. This interface is decoupled from the jMolecules
/// AggregateRoot marker; the framework owns the event API and
/// {@code BaseAggregateRoot} implements it.
public interface EventRecordable {

    /// Drains currently pending domain events with single-step handoff semantics.
    /// Automatic runtime dispatchers invoke this through their event scope;
    /// callers that choose manual dispatch may invoke it directly.
    ///
    /// Atomicity here only means that, under the single-threaded aggregate usage
    /// model, the caller reads the current events and clears the same events in
    /// one method call. It does not imply any concurrency or thread-safety
    /// guarantee. After this method returns, the instance no longer retains the
    /// returned events. An empty list is returned when no events are pending.
    ///
    /// @return snapshot of currently pending domain events, or an empty list
    List<DomainEvent> drainEvents();
}
