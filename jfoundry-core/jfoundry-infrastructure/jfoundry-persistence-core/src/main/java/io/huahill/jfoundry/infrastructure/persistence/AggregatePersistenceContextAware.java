package io.huahill.jfoundry.infrastructure.persistence;

/// Receives the runtime-managed persistence context used by a repository adapter.
public interface AggregatePersistenceContextAware {

    /// Injects the persistence context selected by the runtime integration.
    void setAggregatePersistenceContext(AggregatePersistenceContext persistenceContext);
}
