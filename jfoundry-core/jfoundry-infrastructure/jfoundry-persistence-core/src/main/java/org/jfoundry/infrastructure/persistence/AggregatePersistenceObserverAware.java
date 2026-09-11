package org.jfoundry.infrastructure.persistence;

/// Receives an optional observer for successful aggregate persistence operations.
public interface AggregatePersistenceObserverAware {

    /// Injects the observer selected by an outer integration.
    void setAggregatePersistenceObserver(AggregatePersistenceObserver observer);
}
