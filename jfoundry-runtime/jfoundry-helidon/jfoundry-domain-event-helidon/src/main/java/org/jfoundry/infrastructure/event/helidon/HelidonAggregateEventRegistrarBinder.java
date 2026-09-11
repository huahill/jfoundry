package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregateEventRegistrarAware;

import java.util.Objects;

/// Supplies the Helidon domain-event registrar to CDI-managed persistence adapters.
@Dependent
public final class HelidonAggregateEventRegistrarBinder {

    private final DomainEventContext domainEventContext;
    private final Instance<AggregateEventRegistrarAware> eventRegistrarAwares;

    @Inject
    public HelidonAggregateEventRegistrarBinder(
            DomainEventContext domainEventContext,
            Instance<AggregateEventRegistrarAware> eventRegistrarAwares) {
        this.domainEventContext = Objects.requireNonNull(domainEventContext, "DomainEventContext must not be null");
        this.eventRegistrarAwares = Objects.requireNonNull(
                eventRegistrarAwares, "AggregateEventRegistrarAware instances must not be null");
    }

    void initialize(@Observes @Initialized(ApplicationScoped.class) Object ignored) {
        eventRegistrarAwares.forEach(aware -> aware.setAggregateEventRegistrar(domainEventContext::register));
    }
}
