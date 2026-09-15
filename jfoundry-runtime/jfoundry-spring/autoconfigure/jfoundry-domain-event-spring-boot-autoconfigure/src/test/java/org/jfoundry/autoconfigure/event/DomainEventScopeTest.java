package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventScopeTest {

    private final DomainEventScope scope = new DomainEventScope();

    @Test
    void bindsScopeStateUsingScopedValue() throws NoSuchFieldException {
        assertThat(DomainEventScope.class.getDeclaredField("CURRENT").getType())
                .isEqualTo(ScopedValue.class);
    }

    @Test
    void rejectsRegisteredAggregatesOutsideScope() {
        RecordingAggregate aggregate = new RecordingAggregate(new TestDomainEvent("outside"));

        assertThatThrownBy(() -> scope.register(aggregate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Domain events can only be registered inside an @ApplicationService invocation.");

        assertThat(scope.drainEvents()).isEmpty();
        assertThat(aggregate.drainCount()).isZero();
    }

    @Test
    void drainsRegisteredAggregatesInsideScope() throws Throwable {
        RecordingAggregate aggregate = new RecordingAggregate(new TestDomainEvent("inside"));

        List<DomainEvent> events = scope.invoke(outermost -> {
            assertThat(outermost).isTrue();
            scope.register(aggregate);
            return scope.drainEvents();
        });

        assertThat(events)
                .extracting(event -> ((TestDomainEvent) event).name())
                .containsExactly("inside");
        assertThat(aggregate.drainCount()).isOne();
    }

    @Test
    void nestedScopesReuseOuterScope() throws Throwable {
        RecordingAggregate outer = new RecordingAggregate(new TestDomainEvent("outer"));
        RecordingAggregate inner = new RecordingAggregate(new TestDomainEvent("inner"));

        List<DomainEvent> events = scope.invoke(outermost -> {
            assertThat(outermost).isTrue();
            scope.register(outer);
            scope.invoke(nestedOutermost -> {
                assertThat(nestedOutermost).isFalse();
                scope.register(inner);
                return null;
            });
            return scope.drainEvents();
        });

        assertThat(events)
                .extracting(event -> ((TestDomainEvent) event).name())
                .containsExactly("outer", "inner");
    }

    @Test
    void leavesNoBoundScopeAfterFailure() {
        RecordingAggregate failed = new RecordingAggregate(new TestDomainEvent("failed"));
        RecordingAggregate later = new RecordingAggregate(new TestDomainEvent("later"));

        assertThatThrownBy(() -> scope.invoke(outermost -> {
            scope.register(failed);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> scope.register(later))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Domain events can only be registered inside an @ApplicationService invocation.");

        assertThat(scope.drainEvents()).isEmpty();
        assertThat(failed.drainCount()).isZero();
        assertThat(later.drainCount()).isZero();
    }

    @Test
    void dispatchesOutboxBeforeCommitAndInProcessAfterCommit() throws Throwable {
        RecordingAggregate aggregate = new RecordingAggregate(new TestDomainEvent("transactional"));
        RecordingBeforeCommitDispatcher outbox = new RecordingBeforeCommitDispatcher();
        RecordingDispatcher local = new RecordingDispatcher();
        DomainEventDispatchCoordinator coordinator =
                new DefaultDomainEventDispatchCoordinator(List.of(outbox, local));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            scope.invoke(coordinator, outermost -> {
                scope.register(aggregate);

                assertThat(outbox.events).isEmpty();
                assertThat(local.events).isEmpty();
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization -> synchronization.beforeCommit(false));

                assertThat(outbox.events)
                        .extracting(event -> ((TestDomainEvent) event).name())
                        .containsExactly("transactional");
                assertThat(local.events).isEmpty();

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);

                assertThat(local.events)
                        .extracting(event -> ((TestDomainEvent) event).name())
                        .containsExactly("transactional");

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(synchronization ->
                                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

                assertThat(local.events)
                        .extracting(event -> ((TestDomainEvent) event).name())
                        .containsExactly("transactional");
                return null;
            });
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static final class RecordingAggregate implements EventRecordable {

        private final List<DomainEvent> events;
        private int drainCount;

        private RecordingAggregate(DomainEvent event) {
            this.events = List.of(event);
        }

        @Override
        public List<DomainEvent> drainEvents() {
            drainCount++;
            return events;
        }

        int drainCount() {
            return drainCount;
        }
    }

    private static class RecordingDispatcher implements DomainEventDispatcher {

        final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            this.events.addAll(events);
        }
    }

    private static final class RecordingBeforeCommitDispatcher extends RecordingDispatcher
            implements BeforeCommitDomainEventDispatcher {
    }

    private record TestDomainEvent(String name) implements DomainEvent {
    }
}
