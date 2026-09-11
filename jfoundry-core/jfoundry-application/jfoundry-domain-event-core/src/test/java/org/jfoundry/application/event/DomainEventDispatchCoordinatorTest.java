package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventDispatchCoordinatorTest {

    @Test
    void dispatchesBeforeCommitEventsOnlyToBeforeCommitDispatchers() {
        RecordingDispatcher outbox = new RecordingDispatcher("outbox");
        RecordingDispatcher local = new RecordingDispatcher("local");
        DomainEventDispatchCoordinator coordinator = new DefaultDomainEventDispatchCoordinator(List.of(
                outbox.asBeforeCommit(), local));

        coordinator.dispatchBeforeCommit(List.of(new TestDomainEvent("order-1")));

        assertThat(outbox.calls).containsExactly("outbox:order-1");
        assertThat(local.calls).isEmpty();
    }

    @Test
    void dispatchesAfterCommitEventsOnlyToAfterCommitDispatchers() {
        RecordingDispatcher outbox = new RecordingDispatcher("outbox");
        RecordingDispatcher local = new RecordingDispatcher("local");
        DomainEventDispatchCoordinator coordinator = new DefaultDomainEventDispatchCoordinator(List.of(
                outbox.asBeforeCommit(), local));

        coordinator.dispatchAfterCommit(List.of(new TestDomainEvent("order-1")));

        assertThat(outbox.calls).isEmpty();
        assertThat(local.calls).containsExactly("local:order-1");
    }

    @Test
    void dispatchesWithoutTransactionToAllDispatchersInDeclarationOrder() {
        RecordingDispatcher first = new RecordingDispatcher("first");
        RecordingDispatcher second = new RecordingDispatcher("second");
        DomainEventDispatchCoordinator coordinator = new DefaultDomainEventDispatchCoordinator(List.of(
                first.asBeforeCommit(), second));

        coordinator.dispatchWithoutTransaction(List.of(new TestDomainEvent("order-1")));

        assertThat(first.calls).containsExactly("first:order-1");
        assertThat(second.calls).containsExactly("second:order-1");
    }

    private static final class RecordingDispatcher implements DomainEventDispatcher {

        private final String name;
        private final List<String> calls = new ArrayList<>();

        private RecordingDispatcher(String name) {
            this.name = name;
        }

        private BeforeCommitDomainEventDispatcher asBeforeCommit() {
            return events -> dispatch(events);
        }

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            calls.add(name + ":" + ((TestDomainEvent) events.getFirst()).name());
        }
    }

    private record TestDomainEvent(String name) implements DomainEvent {
    }
}
