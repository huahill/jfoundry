package org.jfoundry.infrastructure.persistence.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;

import java.util.Objects;

/// Supplies the Quarkus transaction-bound context to CDI-managed persistence adapters.
public final class QuarkusAggregatePersistenceContextBinder {

    private final AggregatePersistenceContext persistenceContext;
    private final Instance<AggregatePersistenceContextAware> persistenceContextAwares;

    public QuarkusAggregatePersistenceContextBinder(
            AggregatePersistenceContext persistenceContext,
            Instance<AggregatePersistenceContextAware> persistenceContextAwares) {
        this.persistenceContext = Objects.requireNonNull(
                persistenceContext, "AggregatePersistenceContext must not be null.");
        this.persistenceContextAwares = Objects.requireNonNull(
                persistenceContextAwares, "AggregatePersistenceContextAware instances must not be null.");
    }

    void initialize(@Observes StartupEvent event) {
        persistenceContextAwares.forEach(
                aware -> aware.setAggregatePersistenceContext(persistenceContext));
    }
}
