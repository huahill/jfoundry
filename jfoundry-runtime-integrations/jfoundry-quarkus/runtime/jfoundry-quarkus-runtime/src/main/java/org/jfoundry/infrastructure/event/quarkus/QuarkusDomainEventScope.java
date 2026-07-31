package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.DefaultDomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Thread-bound domain-event scope for a Quarkus application-service invocation.
@ApplicationScoped
public class QuarkusDomainEventScope {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @Inject
    public QuarkusDomainEventScope(TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionSynchronizationRegistry = Objects.requireNonNull(
                transactionSynchronizationRegistry, "Transaction synchronization registry must not be null.");
    }

    <T> T invoke(ScopedOperation<T> operation) throws Exception {
        return invoke(List.of(), operation);
    }

    <T> T invoke(List<DomainEventDispatcher> dispatchers, ScopedOperation<T> operation) throws Exception {
        if (CURRENT.get() != null) {
            return operation.call(false);
        }

        CURRENT.set(new State(dispatchers, transactionSynchronizationRegistry));
        try {
            return operation.call(true);
        } finally {
            CURRENT.remove();
        }
    }

    void register(EventRecordable aggregate) {
        State state = CURRENT.get();
        if (state != null) {
            state.register(aggregate);
        }
    }

    void markFailed() {
        State state = CURRENT.get();
        if (state != null) {
            state.failed = true;
        }
    }

    boolean failed() {
        State state = CURRENT.get();
        return state != null && state.failed;
    }

    List<DomainEvent> drainEvents() {
        State state = CURRENT.get();
        if (state == null) {
            return List.of();
        }
        return state.drainEvents();
    }

    boolean hasTransactionEvents() {
        State state = CURRENT.get();
        return state != null && state.hasTransactionEvents();
    }

    @FunctionalInterface
    interface ScopedOperation<T> {

        T call(boolean outermost) throws Exception;
    }

    private static final class State {

        private final DefaultDomainEventContext context = new DefaultDomainEventContext();
        private final List<DomainEventDispatcher> dispatchers;
        private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
        private boolean failed;

        private State(List<DomainEventDispatcher> dispatchers,
                      TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
            this.dispatchers = List.copyOf(dispatchers);
            this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
        }

        private void register(EventRecordable aggregate) {
            if (isActiveTransaction()) {
                transactionEvents().register(aggregate);
            } else if (transactionSynchronizationRegistry.getTransactionStatus() != Status.STATUS_MARKED_ROLLBACK) {
                context.register(aggregate);
            }
        }

        private List<DomainEvent> drainEvents() {
            TransactionEvents transactionEvents = existingTransactionEvents();
            if (transactionEvents != null) {
                return transactionEvents.events();
            }
            List<DomainEvent> events = new ArrayList<>();
            for (EventRecordable aggregate : context.drainRegistered()) {
                events.addAll(aggregate.drainEvents());
            }
            return List.copyOf(events);
        }

        private boolean hasTransactionEvents() {
            return existingTransactionEvents() != null;
        }

        private boolean isActiveTransaction() {
            return transactionSynchronizationRegistry.getTransactionKey() != null
                    && transactionSynchronizationRegistry.getTransactionStatus() == Status.STATUS_ACTIVE;
        }

        private TransactionEvents transactionEvents() {
            TransactionEvents events = existingTransactionEvents();
            if (events != null) {
                return events;
            }

            TransactionEvents created = new TransactionEvents();
            transactionSynchronizationRegistry.putResource(this, created);
            transactionSynchronizationRegistry.registerInterposedSynchronization(new TransactionEventSynchronization(
                    this, created, dispatchers));
            return created;
        }

        private TransactionEvents existingTransactionEvents() {
            if (!isActiveTransaction()) {
                return null;
            }
            return (TransactionEvents) transactionSynchronizationRegistry.getResource(this);
        }
    }

    private static final class TransactionEvents {

        private final List<EventRecordable> aggregates = new ArrayList<>();
        private final Map<EventRecordable, Boolean> seen = new IdentityHashMap<>();
        private List<DomainEvent> events;

        private void register(EventRecordable aggregate) {
            if (seen.put(aggregate, Boolean.TRUE) == null) {
                aggregates.add(aggregate);
            }
        }

        private List<DomainEvent> events() {
            if (events != null) {
                return events;
            }
            List<DomainEvent> drained = new ArrayList<>();
            for (EventRecordable aggregate : aggregates) {
                drained.addAll(aggregate.drainEvents());
            }
            aggregates.clear();
            seen.clear();
            events = List.copyOf(drained);
            return events;
        }
    }

    private record TransactionEventSynchronization(State state, TransactionEvents events,
                                                   List<DomainEventDispatcher> dispatchers)
            implements Synchronization {

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            if (!state.failed && status == Status.STATUS_COMMITTED) {
                dispatch(events.events(), false);
            }
        }

        private void dispatch(List<DomainEvent> events, boolean beforeCommit) {
            if (events.isEmpty()) {
                return;
            }
            dispatchers.stream()
                    .filter(dispatcher -> (dispatcher instanceof BeforeCommitDomainEventDispatcher) == beforeCommit)
                    .forEach(dispatcher -> dispatcher.dispatch(events));
        }
    }
}
