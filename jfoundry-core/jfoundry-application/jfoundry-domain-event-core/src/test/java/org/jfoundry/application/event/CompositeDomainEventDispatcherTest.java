package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeDomainEventDispatcherTest {

    @Test
    void dispatchesEventsToDelegatesInOrder() {
        List<String> calls = new ArrayList<>();
        RecordingDispatcher first = new RecordingDispatcher("first", calls);
        RecordingDispatcher second = new RecordingDispatcher("second", calls);
        CompositeDomainEventDispatcher dispatcher = new CompositeDomainEventDispatcher(List.of(first, second));
        TestDomainEvent event = new TestDomainEvent("order-1");

        dispatcher.dispatch(List.of(event));

        assertThat(calls).containsExactly("first:order-1", "second:order-1");
    }

    @Test
    void ignoresEmptyEventBatch() {
        RecordingDispatcher delegate = new RecordingDispatcher("delegate", new ArrayList<>());
        CompositeDomainEventDispatcher dispatcher = new CompositeDomainEventDispatcher(List.of(delegate));

        dispatcher.dispatch(List.of());

        assertThat(delegate.dispatchedEvents).isEmpty();
    }

    @Test
    void rejectsNullEventList() {
        CompositeDomainEventDispatcher dispatcher = new CompositeDomainEventDispatcher(List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain events must not be null.");
    }

    @Test
    void rejectsNullEventElementBeforeDispatching() {
        RecordingDispatcher delegate = new RecordingDispatcher("delegate", new ArrayList<>());
        CompositeDomainEventDispatcher dispatcher = new CompositeDomainEventDispatcher(List.of(delegate));

        assertThatThrownBy(() -> dispatcher.dispatch(Arrays.asList(new TestDomainEvent("order-1"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain event must not be null.");

        assertThat(delegate.dispatchedEvents).isEmpty();
    }

    private static final class RecordingDispatcher implements DomainEventDispatcher {

        private final String name;
        private final List<String> calls;
        private final List<TestDomainEvent> dispatchedEvents = new ArrayList<>();

        private RecordingDispatcher(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            for (DomainEvent event : events) {
                TestDomainEvent testEvent = (TestDomainEvent) event;
                dispatchedEvents.add(testEvent);
                calls.add(name + ":" + testEvent.aggregateId());
            }
        }
    }

    record TestDomainEvent(String aggregateId) implements DomainEvent {
    }
}
