package io.huahill.jfoundry.test;

import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/// Test utility for capturing aggregate events.
/// <p>
/// Used with {@link DomainEventDispatcherStub} to assert whether events were handed off and dispatched.
public class DomainEventCapture {

    private final List<DomainEvent> captured = new ArrayList<>();

    public void capture(DomainEvent event) {
        captured.add(event);
    }

    public void captureAll(DomainEvent... events) {
        for (DomainEvent event : events) {
            captured.add(event);
        }
    }

    public void captureAll(List<? extends DomainEvent> events) {
        captured.addAll(events);
    }

    public List<DomainEvent> drained() {
        List<DomainEvent> snapshot = List.copyOf(captured);
        captured.clear();
        return snapshot;
    }

    public List<DomainEvent> snapshot() {
        return List.copyOf(captured);
    }

    public int size() {
        return captured.size();
    }
}
