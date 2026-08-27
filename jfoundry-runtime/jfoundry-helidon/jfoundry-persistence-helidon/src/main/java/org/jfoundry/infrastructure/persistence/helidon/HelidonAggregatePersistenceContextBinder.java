package org.jfoundry.infrastructure.persistence.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;

import java.util.Objects;

/// Supplies the Helidon transaction-bound context to CDI-managed persistence adapters.
@Dependent
public final class HelidonAggregatePersistenceContextBinder {

    private final AggregatePersistenceContext persistenceContext;
    private final Instance<AggregatePersistenceContextAware> persistenceContextAwares;

    @Inject
    public HelidonAggregatePersistenceContextBinder(
            AggregatePersistenceContext persistenceContext,
            Instance<AggregatePersistenceContextAware> persistenceContextAwares) {
        this.persistenceContext = Objects.requireNonNull(persistenceContext, "AggregatePersistenceContext must not be null");
        this.persistenceContextAwares = Objects.requireNonNull(
                persistenceContextAwares, "AggregatePersistenceContextAware instances must not be null");
    }

    void initialize(@Observes @Initialized(ApplicationScoped.class) Object ignored) {
        persistenceContextAwares.forEach(aware -> aware.setAggregatePersistenceContext(persistenceContext));
    }
}
