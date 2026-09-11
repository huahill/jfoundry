package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.DefaultDomainEventContext;
import org.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Dynamically scoped domain-event context coordinated with Spring transactions.
public class DomainEventScope {

    private static final ScopedValue<State> CURRENT = ScopedValue.newInstance();

    <T> T invoke(ScopedOperation<T> operation) throws Throwable {
        return invoke(new DefaultDomainEventDispatchCoordinator(List.of()), operation);
    }

    <T> T invoke(DomainEventDispatchCoordinator coordinator, ScopedOperation<T> operation) throws Throwable {
        if (CURRENT.isBound()) {
            return operation.get(false);
        }
        return ScopedValue.where(CURRENT, new State(coordinator)).call(() -> {
            try {
                return operation.get(true);
            } catch (Throwable throwable) {
                return rethrow(throwable);
            }
        });
    }

    void register(EventRecordable aggregate) {
        State state = current();
        if (state == null) {
            DomainEventContext.requireActiveScope();
            return;
        }
        state.register(aggregate);
    }

    void markFailed() {
        State state = current();
        if (state != null) {
            state.failed = true;
        }
    }

    boolean failed() {
        State state = current();
        return state != null && state.failed;
    }

    List<DomainEvent> drainEvents() {
        State state = current();
        if (state == null) {
            return List.of();
        }
        return state.drainEvents();
    }

    boolean hasTransactionEvents() {
        State state = current();
        return state != null && state.hasTransactionEvents();
    }

    private State current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T rethrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private static final class State {

        private final DefaultDomainEventContext context = new DefaultDomainEventContext();
        private final DomainEventDispatchCoordinator coordinator;
        private boolean failed;

        private State(DomainEventDispatchCoordinator coordinator) {
            this.coordinator = Objects.requireNonNull(coordinator, "Domain-event coordinator must not be null.");
        }

        private void register(EventRecordable aggregate) {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                transactionEvents().register(aggregate);
                return;
            }
            context.register(aggregate);
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
            return TransactionSynchronizationManager.hasResource(this);
        }

        private TransactionEvents transactionEvents() {
            TransactionEvents events = existingTransactionEvents();
            if (events != null) {
                return events;
            }

            TransactionEvents created = new TransactionEvents();
            TransactionSynchronizationManager.bindResource(this, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionEventSynchronization(
                    this, created, coordinator));
            return created;
        }

        private TransactionEvents existingTransactionEvents() {
            if (!TransactionSynchronizationManager.hasResource(this)) {
                return null;
            }
            return (TransactionEvents) TransactionSynchronizationManager.getResource(this);
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
                                                   DomainEventDispatchCoordinator coordinator)
            implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            List<DomainEvent> domainEvents = events.events();
            if (!state.failed) {
                coordinator.dispatchBeforeCommit(domainEvents);
            }
        }

        @Override
        public void afterCommit() {
            if (!state.failed) {
                coordinator.dispatchAfterCommit(events.events());
            }
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                events.events();
            }
            unbindIfCurrent();
        }

        @Override
        public void suspend() {
            unbindIfCurrent();
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(state, events);
        }

        private void unbindIfCurrent() {
            if (TransactionSynchronizationManager.hasResource(state)
                    && TransactionSynchronizationManager.getResource(state) == events) {
                TransactionSynchronizationManager.unbindResource(state);
            }
        }
    }

    @FunctionalInterface
    interface ScopedOperation<T> {
        T get(boolean outermost) throws Throwable;
    }

}
