package io.huahill.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.huahill.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import io.huahill.jfoundry.application.event.DomainEventDispatcher;

/// Quarkus CDI registration for the application domain-event coordinator.
@ApplicationScoped
public class DelegatingDomainEventDispatchCoordinator extends DefaultDomainEventDispatchCoordinator {

    protected DelegatingDomainEventDispatchCoordinator() {
        super(java.util.List.of());
    }

    @Inject
    public DelegatingDomainEventDispatchCoordinator(@Any Instance<DomainEventDispatcher> dispatchers) {
        super(dispatchers.stream().toList());
    }
}
