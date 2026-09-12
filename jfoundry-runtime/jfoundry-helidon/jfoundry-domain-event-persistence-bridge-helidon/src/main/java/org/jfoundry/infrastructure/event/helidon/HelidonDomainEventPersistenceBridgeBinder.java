package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserverAware;
import org.jfoundry.infrastructure.persistence.event.DomainEventAggregatePersistenceObserver;

import java.util.Objects;

/// Supplies the Helidon Domain Event persistence bridge to CDI-managed persistence adapters.
@Dependent
public final class HelidonDomainEventPersistenceBridgeBinder {

    private final DomainEventContext domainEventContext;
    private final Instance<AggregatePersistenceObserverAware> persistenceObserverAwares;

    @Inject
    public HelidonDomainEventPersistenceBridgeBinder(
            DomainEventContext domainEventContext,
            Instance<AggregatePersistenceObserverAware> persistenceObserverAwares) {
        this.domainEventContext = Objects.requireNonNull(domainEventContext, "DomainEventContext must not be null");
        this.persistenceObserverAwares = Objects.requireNonNull(
                persistenceObserverAwares, "AggregatePersistenceObserverAware instances must not be null");
    }

    void initialize(@Observes @Initialized(ApplicationScoped.class) Object ignored) {
        var observer = new DomainEventAggregatePersistenceObserver(domainEventContext);
        persistenceObserverAwares.forEach(aware -> aware.setAggregatePersistenceObserver(observer));
    }
}
