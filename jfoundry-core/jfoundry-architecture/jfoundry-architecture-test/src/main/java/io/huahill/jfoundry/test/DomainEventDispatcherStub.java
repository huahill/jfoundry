package io.huahill.jfoundry.test;

import io.huahill.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Test double for {@link DomainEventDispatcher}.
/// <p>
/// Does not depend on the Spring container or transaction synchronization; it receives events
/// immediately and forwards them to {@link DomainEventCapture}.
public class DomainEventDispatcherStub implements DomainEventDispatcher {

    private final DomainEventCapture capture;

    public DomainEventDispatcherStub(DomainEventCapture capture) {
        this.capture = capture;
    }

    @Override
    public void dispatch(List<? extends DomainEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Domain events must not be null.");
        }
        capture.captureAll(events);
    }
}
