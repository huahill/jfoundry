package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.event.cdi.CdiDomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

/// Quarkus CDI registration for the shared CDI domain-event dispatcher.
@ApplicationScoped
public class DelegatingCdiDomainEventDispatcher extends CdiDomainEventDispatcher {

    // Non-private no-arg constructor required for CDI client proxies.
    protected DelegatingCdiDomainEventDispatcher() {
    }

    @Inject
    public DelegatingCdiDomainEventDispatcher(Event<DomainEvent> events) {
        super(events);
    }
}
