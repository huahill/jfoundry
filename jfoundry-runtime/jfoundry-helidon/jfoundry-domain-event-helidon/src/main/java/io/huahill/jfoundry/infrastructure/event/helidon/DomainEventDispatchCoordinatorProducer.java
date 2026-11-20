package io.huahill.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import io.huahill.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import io.huahill.jfoundry.application.event.DomainEventDispatchCoordinator;
import io.huahill.jfoundry.application.event.DomainEventDispatcher;

/// Produces the domain-event coordinator for Helidon.
@Dependent
public final class DomainEventDispatchCoordinatorProducer {

    @Inject
    DomainEventDispatchCoordinatorProducer() {
    }

    @Produces
    @Dependent
    DomainEventDispatchCoordinator domainEventDispatchCoordinator(
            @Any Instance<DomainEventDispatcher> dispatchers) {
        return new DefaultDomainEventDispatchCoordinator(dispatchers.stream().toList());
    }
}
