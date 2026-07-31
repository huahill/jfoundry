package org.jfoundry.infrastructure.outbox.spring.externalization;

import org.jfoundry.application.event.DomainEventBatch;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.function.Supplier;

/// Records domain events into the transactional outbox.
public class OutboxDomainEventDispatcher implements DomainEventDispatcher {

    private final Supplier<? extends DomainEventOutboxRecorder> outboxRecorderSupplier;

    public OutboxDomainEventDispatcher(DomainEventOutboxRecorder outboxRecorder) {
        this(() -> outboxRecorder);
    }

    public OutboxDomainEventDispatcher(Supplier<? extends DomainEventOutboxRecorder> outboxRecorderSupplier) {
        this.outboxRecorderSupplier = outboxRecorderSupplier;
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }
        outboxRecorderSupplier.get().record(eventBatch);
    }
}
