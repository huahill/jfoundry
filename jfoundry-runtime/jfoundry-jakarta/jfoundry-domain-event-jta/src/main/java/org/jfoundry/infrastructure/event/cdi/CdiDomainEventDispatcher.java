package org.jfoundry.infrastructure.event.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jfoundry.application.event.DomainEventBatch;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.Objects;

/// Publishes local domain events through CDI immediately when invoked.
///
/// Helidon produces this type directly. Quarkus registers a runtime subclass
/// because `AdditionalBeanBuildItem` cannot see this class from the JTA jar.
@ApplicationScoped
public class CdiDomainEventDispatcher implements DomainEventDispatcher {

    private final Event<DomainEvent> events;

    // Non-private no-arg constructor required for CDI client proxies.
    protected CdiDomainEventDispatcher() {
        this.events = null;
    }

    @Inject
    public CdiDomainEventDispatcher(Event<DomainEvent> events) {
        this.events = Objects.requireNonNull(events, "CDI event publisher must not be null.");
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        eventBatch.forEach(this.events::fire);
    }
}
