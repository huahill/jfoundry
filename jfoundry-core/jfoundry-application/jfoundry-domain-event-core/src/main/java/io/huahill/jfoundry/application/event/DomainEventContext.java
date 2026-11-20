package io.huahill.jfoundry.application.event;

import io.huahill.jfoundry.domain.event.EventRecordable;

/// Registers aggregates touched within an application-service boundary.
public interface DomainEventContext {

    void register(EventRecordable aggregate);

    /// Fails when domain events are recorded outside an application-service scope.
    static void requireActiveScope() {
        throw new IllegalStateException(
                "Domain events can only be registered inside an @ApplicationService invocation.");
    }
}
