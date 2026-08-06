package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.domain.event.EventRecordable;

import java.util.Objects;

/// Default CDI adapter that registers aggregates in the active Quarkus event scope.
@ApplicationScoped
public final class QuarkusDomainEventContext implements DomainEventContext {

    private final QuarkusDomainEventScope scope;

    public QuarkusDomainEventContext(QuarkusDomainEventScope scope) {
        this.scope = Objects.requireNonNull(scope, "QuarkusDomainEventScope must not be null.");
    }

    @Override
    public void register(EventRecordable aggregate) {
        scope.register(aggregate);
    }
}
