package org.jfoundry.infrastructure.outbox.quarkus.externalization;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.event.DomainEventBatch;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Optionally records externalized domain events through the Quarkus Outbox assembly.
@ApplicationScoped
public final class OutboxDomainEventDispatcher implements BeforeCommitDomainEventDispatcher {

    private final Instance<DomainEventOutboxRecorder> outboxRecorder;
    private final boolean enabled;

    @Inject
    public OutboxDomainEventDispatcher(
            Instance<DomainEventOutboxRecorder> outboxRecorder,
            @ConfigProperty(name = "jfoundry.domain.event.dispatch.outbox.enabled", defaultValue = "false")
            boolean enabled) {
        this.outboxRecorder = outboxRecorder;
        this.enabled = enabled;
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        if (!enabled) {
            return;
        }
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        if (!outboxRecorder.isResolvable()) {
            throw new IllegalStateException(
                    "Automatic domain-event externalization requires a DomainEventOutboxRecorder CDI bean");
        }
        outboxRecorder.get().record(eventBatch);
    }
}
