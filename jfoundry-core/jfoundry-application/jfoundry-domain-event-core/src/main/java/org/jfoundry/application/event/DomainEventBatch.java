package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared domain event batch validation for dispatcher implementations.
 */
public final class DomainEventBatch {

    private DomainEventBatch() {
    }

    public static List<DomainEvent> copyAndValidate(List<? extends DomainEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Domain events must not be null.");
        }

        List<DomainEvent> eventBatch = new ArrayList<>(events.size());
        for (DomainEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("Domain event must not be null.");
            }
            eventBatch.add(event);
        }
        return eventBatch;
    }
}
