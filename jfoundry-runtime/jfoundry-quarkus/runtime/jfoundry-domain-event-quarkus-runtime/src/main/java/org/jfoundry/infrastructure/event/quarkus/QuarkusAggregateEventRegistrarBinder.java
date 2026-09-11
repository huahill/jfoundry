package org.jfoundry.infrastructure.event.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregateEventRegistrarAware;

import java.util.Objects;

/// Supplies the Quarkus domain-event registrar to CDI-managed persistence adapters.
public final class QuarkusAggregateEventRegistrarBinder {

    private final DomainEventContext domainEventContext;
    private final Instance<AggregateEventRegistrarAware> eventRegistrarAwares;

    public QuarkusAggregateEventRegistrarBinder(
            DomainEventContext domainEventContext,
            Instance<AggregateEventRegistrarAware> eventRegistrarAwares) {
        this.domainEventContext = Objects.requireNonNull(
                domainEventContext, "DomainEventContext must not be null.");
        this.eventRegistrarAwares = Objects.requireNonNull(
                eventRegistrarAwares, "AggregateEventRegistrarAware instances must not be null.");
    }

    void initialize(@Observes StartupEvent event) {
        eventRegistrarAwares.forEach(
                aware -> aware.setAggregateEventRegistrar(domainEventContext::register));
    }
}
