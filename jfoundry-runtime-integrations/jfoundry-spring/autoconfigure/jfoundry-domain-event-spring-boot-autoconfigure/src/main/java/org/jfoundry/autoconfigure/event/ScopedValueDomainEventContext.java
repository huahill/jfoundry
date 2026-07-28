package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.domain.event.EventRecordable;

public class ScopedValueDomainEventContext implements DomainEventContext {

    private final DomainEventScope scope;

    public ScopedValueDomainEventContext(DomainEventScope scope) {
        this.scope = scope;
    }

    @Override
    public void register(EventRecordable aggregate) {
        scope.register(aggregate);
    }
}
