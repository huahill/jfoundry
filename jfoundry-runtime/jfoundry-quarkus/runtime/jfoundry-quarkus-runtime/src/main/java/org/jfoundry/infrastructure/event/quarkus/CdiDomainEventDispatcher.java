package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.application.event.DomainEventBatch;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.Objects;

/// Publishes local domain events through CDI after the current transaction commits.
@ApplicationScoped
public final class CdiDomainEventDispatcher implements DomainEventDispatcher {

    private final Event<DomainEvent> events;
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public CdiDomainEventDispatcher(
            Event<DomainEvent> events,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.events = Objects.requireNonNull(events, "CDI event publisher must not be null.");
        this.transactionSynchronizationRegistry = Objects.requireNonNull(
                transactionSynchronizationRegistry, "Transaction synchronization registry must not be null.");
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(events);
        if (eventBatch.isEmpty()) {
            return;
        }

        if (!isActiveTransaction()) {
            publish(eventBatch);
            return;
        }

        transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    publish(eventBatch);
                }
            }
        });
    }

    private boolean isActiveTransaction() {
        return transactionSynchronizationRegistry.getTransactionKey() != null
                && transactionSynchronizationRegistry.getTransactionStatus() == Status.STATUS_ACTIVE;
    }

    private void publish(List<DomainEvent> events) {
        events.forEach(this.events::fire);
    }
}
