package org.jfoundry.application.event;

import org.jfoundry.domain.event.EventRecordable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic in-memory {@link DomainEventContext} with registration de-duplication.
 */
public final class DefaultDomainEventContext implements DomainEventContext {

    private final List<EventRecordable> aggregates = new ArrayList<>();
    private final Map<EventRecordable, Boolean> seen = new IdentityHashMap<>();

    @Override
    public void register(EventRecordable aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Aggregate must not be null.");
        }
        if (!seen.containsKey(aggregate)) {
            seen.put(aggregate, Boolean.TRUE);
            aggregates.add(aggregate);
        }
    }

    public List<EventRecordable> drainRegistered() {
        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<EventRecordable> drained = List.copyOf(aggregates);
        aggregates.clear();
        seen.clear();
        return drained;
    }
}
