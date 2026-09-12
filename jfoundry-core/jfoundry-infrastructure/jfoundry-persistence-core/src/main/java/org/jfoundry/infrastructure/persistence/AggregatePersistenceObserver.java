package org.jfoundry.infrastructure.persistence;

import org.jmolecules.ddd.types.AggregateRoot;

/// Observes an aggregate after a complete persistence operation succeeds.
///
/// This callback is intentionally independent of Domain Event types. Optional integrations may
/// adapt it to another capability, while generic persistence remains usable on its own.
@FunctionalInterface
public interface AggregatePersistenceObserver {

    /// Observes one aggregate after its persistence operation has completed successfully.
    void afterPersisted(AggregateRoot<?, ?> aggregate);
}
