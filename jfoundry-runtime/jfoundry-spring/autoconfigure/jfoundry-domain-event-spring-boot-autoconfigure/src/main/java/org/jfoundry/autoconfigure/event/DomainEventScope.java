package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.DefaultDomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DomainEventScope {

    private static final ScopedValue<State> CURRENT = ScopedValue.newInstance();

    <T> T invoke(ScopedOperation<T> operation) throws Throwable {
        return invoke(null, operation);
    }

    <T> T invoke(DomainEventDispatcher dispatcher, ScopedOperation<T> operation) throws Throwable {
        if (CURRENT.isBound()) {
            return operation.get(false);
        }
        return ScopedValue.where(CURRENT, new State(dispatcher)).call(() -> {
            try {
                return operation.get(true);
            } catch (Throwable throwable) {
                return rethrow(throwable);
            }
        });
    }

    void register(EventRecordable aggregate) {
        State state = current();
        if (state != null) {
            state.context.register(aggregate);
            state.registerTransactionEvent(aggregate);
        }
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
        List<DomainEvent> events = new ArrayList<>();
        for (EventRecordable aggregate : state.context.drainRegistered()) {
            events.addAll(aggregate.drainEvents());
        }
        return List.copyOf(events);
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
        private final DomainEventDispatcher dispatcher;
        private boolean failed;

        private State(DomainEventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        private void registerTransactionEvent(EventRecordable aggregate) {
            if (dispatcher == null
                    || !TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) {
                return;
            }
            TransactionEvents events = currentTransactionEvents();
            events.register(aggregate);
        }

        private TransactionEvents currentTransactionEvents() {
            TransactionEvents events = (TransactionEvents) TransactionSynchronizationManager.getResource(this);
            if (events != null) {
                return events;
            }

            TransactionEvents created = new TransactionEvents();
            TransactionSynchronizationManager.bindResource(this, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionEventSynchronization(
                    this, created, dispatcher));
            return created;
        }
    }

    private static final class TransactionEvents {

        private final List<EventRecordable> aggregates = new ArrayList<>();
        private final Map<EventRecordable, Boolean> seen = new IdentityHashMap<>();

        private void register(EventRecordable aggregate) {
            if (seen.put(aggregate, Boolean.TRUE) == null) {
                aggregates.add(aggregate);
            }
        }

        private List<DomainEvent> drainEvents() {
            List<DomainEvent> events = new ArrayList<>();
            for (EventRecordable aggregate : aggregates) {
                events.addAll(aggregate.drainEvents());
            }
            aggregates.clear();
            seen.clear();
            return List.copyOf(events);
        }
    }

    private record TransactionEventSynchronization(State state, TransactionEvents events,
                                                   DomainEventDispatcher dispatcher)
            implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            List<DomainEvent> domainEvents = events.drainEvents();
            if (!domainEvents.isEmpty()) {
                dispatcher.dispatch(domainEvents);
            }
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                events.drainEvents();
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
