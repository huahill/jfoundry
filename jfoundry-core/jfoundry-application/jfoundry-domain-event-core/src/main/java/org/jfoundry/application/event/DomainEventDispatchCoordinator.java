package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Coordinates domain-event dispatch across transaction lifecycle phases.
public interface DomainEventDispatchCoordinator {

    /// Dispatches only dispatchers that must complete before transaction commit.
    void dispatchBeforeCommit(List<? extends DomainEvent> events);

    /// Dispatches only dispatchers that run after a successful transaction commit.
    void dispatchAfterCommit(List<? extends DomainEvent> events);

    /// Dispatches events to every configured dispatcher when no transaction is active.
    void dispatchWithoutTransaction(List<? extends DomainEvent> events);
}
