package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.DefaultDomainEventContext;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;

public class DomainEventScope {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    <T> T invoke(ScopedOperation<T> operation) throws Throwable {
        if (CURRENT.get() != null) {
            return operation.get(false);
        }
        CURRENT.set(new State());
        try {
            return operation.get(true);
        } finally {
            CURRENT.remove();
        }
    }

    void register(EventRecordable aggregate) {
        State state = current();
        if (state != null) {
            state.context.register(aggregate);
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
        return CURRENT.get();
    }

    private static final class State {
        private final DefaultDomainEventContext context = new DefaultDomainEventContext();
        private boolean failed;
    }

    @FunctionalInterface
    interface ScopedOperation<T> {
        T get(boolean outermost) throws Throwable;
    }

}
