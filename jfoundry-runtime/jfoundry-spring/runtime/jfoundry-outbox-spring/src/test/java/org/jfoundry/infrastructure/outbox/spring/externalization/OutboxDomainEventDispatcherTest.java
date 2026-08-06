package org.jfoundry.infrastructure.outbox.spring.externalization;

import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxDomainEventDispatcherTest {

    @Test
    void recordsEventsIntoOutbox() {
        RecordingOutboxRecorder outboxRecorder = new RecordingOutboxRecorder();
        OutboxDomainEventDispatcher dispatcher = new OutboxDomainEventDispatcher(() -> outboxRecorder);
        TestDomainEvent event = new TestDomainEvent("order-1");

        dispatcher.dispatch(List.of(event));

        assertThat(outboxRecorder.recordedEvents).containsExactly(event);
    }

    @Test
    void doesNothingForEmptyEventBatch() {
        RecordingOutboxRecorder outboxRecorder = new RecordingOutboxRecorder();
        OutboxDomainEventDispatcher dispatcher = new OutboxDomainEventDispatcher(() -> outboxRecorder);

        dispatcher.dispatch(List.of());

        assertThat(outboxRecorder.recordCallCount).isZero();
        assertThat(outboxRecorder.recordedEvents).isEmpty();
    }

    @Test
    void resolvesTheOutboxRecorderOnlyWhenEventsAreDispatched() {
        RecordingOutboxRecorder outboxRecorder = new RecordingOutboxRecorder();
        AtomicInteger resolutions = new AtomicInteger();
        OutboxDomainEventDispatcher dispatcher = new OutboxDomainEventDispatcher(() -> {
            resolutions.incrementAndGet();
            return outboxRecorder;
        });

        dispatcher.dispatch(List.of());
        assertThat(resolutions).hasValue(0);

        dispatcher.dispatch(List.of(new TestDomainEvent("order-1")));

        assertThat(resolutions).hasValue(1);
        assertThat(outboxRecorder.recordCallCount).isEqualTo(1);
    }

    @Test
    void rejectsNullEventList() {
        OutboxDomainEventDispatcher dispatcher = new OutboxDomainEventDispatcher(
                RecordingOutboxRecorder::new);

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain events must not be null.");
    }

    @Test
    void rejectsNullEventElementBeforeRecording() {
        RecordingOutboxRecorder outboxRecorder = new RecordingOutboxRecorder();
        OutboxDomainEventDispatcher dispatcher = new OutboxDomainEventDispatcher(() -> outboxRecorder);

        assertThatThrownBy(() -> dispatcher.dispatch(Arrays.asList(new TestDomainEvent("order-1"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain event must not be null.");

        assertThat(outboxRecorder.recordedEvents).isEmpty();
    }

    private static final class RecordingOutboxRecorder implements DomainEventOutboxRecorder {

        private final List<DomainEvent> recordedEvents = new ArrayList<>();
        private int recordCallCount;

        @Override
        public void record(List<? extends DomainEvent> events) {
            recordCallCount++;
            recordedEvents.addAll(events);
        }
    }

    record TestDomainEvent(String aggregateId) implements DomainEvent {
    }
}
