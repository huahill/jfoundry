package io.huahill.jfoundry.autoconfigure.event;

import io.huahill.jfoundry.application.event.DomainEventContext;
import io.huahill.jfoundry.domain.event.EventRecordable;

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
