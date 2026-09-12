package org.jfoundry.infrastructure.event.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserverAware;
import org.jfoundry.infrastructure.persistence.event.DomainEventAggregatePersistenceObserver;

import java.util.Objects;

/// Supplies the Quarkus domain-event persistence bridge to CDI-managed persistence adapters.
public final class QuarkusDomainEventPersistenceBridgeBinder {

    private final DomainEventContext domainEventContext;
    private final Instance<AggregatePersistenceObserverAware> persistenceObserverAwares;

    public QuarkusDomainEventPersistenceBridgeBinder(
            DomainEventContext domainEventContext,
            Instance<AggregatePersistenceObserverAware> persistenceObserverAwares) {
        this.domainEventContext = Objects.requireNonNull(
                domainEventContext, "DomainEventContext must not be null.");
        this.persistenceObserverAwares = Objects.requireNonNull(
                persistenceObserverAwares, "AggregatePersistenceObserverAware instances must not be null.");
    }

    void initialize(@Observes StartupEvent event) {
        var observer = new DomainEventAggregatePersistenceObserver(domainEventContext);
        persistenceObserverAwares.forEach(
                aware -> aware.setAggregatePersistenceObserver(observer));
    }
}
