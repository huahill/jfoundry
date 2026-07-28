package org.jfoundry.domain.entity.agg;

import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/// Base class for aggregate roots.
///
/// Provides common aggregate root behavior:
/// - identifier management
/// - domain event recording and draining
/// - jMolecules AggregateRoot marker plus EventRecordable capability
/// <p>
/// Aggregate roots explicitly record domain events that occur inside their own
/// business behavior. Recording an event does not dispatch, externalize, or
/// persist it.
/// <p>
/// A supported runtime can register a persisted aggregate and automatically
/// drain its events after the outermost successful application-service boundary.
/// A framework-neutral or manual runtime host can instead call
/// {@link #drainEvents()} and dispatch the returned events itself. The atomicity
/// described here is limited to read-and-clear semantics under a single-threaded
/// aggregate usage model; it is not a thread-safety guarantee.
///
/// @param <T> aggregate root self type
/// @param <ID> identifier type
///
public abstract class BaseAggregateRoot<T extends AggregateRoot<T, ID>, ID extends Identifier>
        implements AggregateRoot<T, ID>, EventRecordable {

    /// Identifier.
    private ID id;

    /// Domain event list.
    /// <p>
    /// {@code transient}: excluded from serialization to keep persistence and
    /// cache state from being polluted by pending events.
    /// <p>
    /// Thread-safety contract: aggregate roots are not thread-safe, and the event
    /// list uses {@link ArrayList}. Business code must not share one aggregate
    /// root instance across threads, including async jobs and message listeners.
    /// Pass aggregate state across threads through immutable snapshots or by
    /// reloading the aggregate instance.
    protected transient List<DomainEvent> events;

    public BaseAggregateRoot(ID id) {
        this.id = id;
    }

    @Override
    public ID getId() {
        return id;
    }

    /// Reassigns the identifier.
    /// <p>
    /// This method is only for subclasses and persistence conversion scenarios.
    /// Business code should prefer assigning aggregate identifiers through
    /// constructors.
    ///
    /// @param id identifier
    protected void identify(ID id) {
        this.id = id;
    }

    /// Records a domain event.
    ///
    /// Records a domain event that occurred inside this aggregate's business
    /// behavior. This method only captures the fact; a runtime or manual host
    /// later drains and dispatches it at an application boundary.
    ///
    /// @param event domain event
    protected void recordEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null.");
        }
        if (events == null) {
            events = new ArrayList<>();
        }
        events.add(event);
    }

    @Override
    public List<DomainEvent> drainEvents() {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<DomainEvent> drainedEvents = List.copyOf(events);
        events.clear();
        return drainedEvents;
    }
}
