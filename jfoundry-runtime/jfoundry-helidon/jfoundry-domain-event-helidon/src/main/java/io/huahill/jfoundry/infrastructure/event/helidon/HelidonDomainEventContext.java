package io.huahill.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import io.huahill.jfoundry.application.event.DomainEventContext;
import io.huahill.jfoundry.domain.event.EventRecordable;

import java.util.Objects;

/// Default CDI adapter that registers aggregates in the active Helidon event scope.
@Dependent
public final class HelidonDomainEventContext implements DomainEventContext {

    private final HelidonDomainEventScope scope;

    @Inject
    public HelidonDomainEventContext(HelidonDomainEventScope scope) {
        this.scope = Objects.requireNonNull(scope, "HelidonDomainEventScope must not be null");
    }

    @Override
    public void register(EventRecordable aggregate) {
        scope.register(aggregate);
    }
}
