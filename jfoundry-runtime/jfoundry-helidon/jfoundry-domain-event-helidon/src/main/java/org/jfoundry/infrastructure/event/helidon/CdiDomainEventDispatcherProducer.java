package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.event.cdi.CdiDomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

/// Produces the shared CDI domain-event dispatcher for Helidon.
@Dependent
public final class CdiDomainEventDispatcherProducer {

    @Inject
    CdiDomainEventDispatcherProducer() {
    }

    @Produces
    @Dependent
    CdiDomainEventDispatcher cdiDomainEventDispatcher(Event<DomainEvent> events) {
        return new CdiDomainEventDispatcher(events);
    }

}
