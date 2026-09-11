package org.jfoundry.infrastructure.event.jta;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JtaDomainEventScopeTest {

    @Test
    void bindsScopeStateUsingScopedValue() throws NoSuchFieldException {
        assertThat(JtaDomainEventScope.class.getDeclaredField("CURRENT").getType())
                .isEqualTo(ScopedValue.class);
    }

    @Test
    void rejectsRegisteredAggregatesOutsideScope() {
        JtaDomainEventScope scope = new JtaDomainEventScope(new RecordingTransactionSynchronizationRegistry());
        RecordingAggregate aggregate = new RecordingAggregate(new TestEvent("outside"));

        assertThatThrownBy(() -> scope.register(aggregate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Domain events can only be registered inside an @ApplicationService invocation.");

        assertThat(scope.drainEvents()).isEmpty();
        assertThat(aggregate.drainCount()).isZero();
    }

    @Test
    void leavesNoBoundScopeAfterFailure() {
        JtaDomainEventScope scope = new JtaDomainEventScope(new RecordingTransactionSynchronizationRegistry());
        RecordingAggregate failed = new RecordingAggregate(new TestEvent("failed"));
        RecordingAggregate later = new RecordingAggregate(new TestEvent("later"));

        assertThatThrownBy(() -> scope.invoke(new DefaultDomainEventDispatchCoordinator(List.of()), outermost -> {
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
    void dispatchesOutboxBeforeCompletionAndInProcessAfterCommit() throws Exception {
        RecordingTransactionSynchronizationRegistry transactionRegistry =
                new RecordingTransactionSynchronizationRegistry();
        transactionRegistry.activate();
        JtaDomainEventScope scope = new JtaDomainEventScope(transactionRegistry);
        RecordingBeforeCommitDispatcher outbox = new RecordingBeforeCommitDispatcher();
        RecordingAfterCommitDispatcher local = new RecordingAfterCommitDispatcher();
        DomainEventDispatchCoordinator coordinator =
                new DefaultDomainEventDispatchCoordinator(List.of(outbox, local));

        scope.invoke(coordinator, outermost -> {
            scope.register(new RecordingAggregate(new TestEvent("confirmed")));

            assertThat(outbox.events).isEmpty();
            assertThat(local.events).isEmpty();

            transactionRegistry.beforeCompletion();
            assertThat(outbox.events).extracting(event -> ((TestEvent) event).name())
                    .containsExactly("confirmed");
            assertThat(local.events).isEmpty();

            transactionRegistry.afterCompletion(Status.STATUS_COMMITTED);
            assertThat(local.events).extracting(event -> ((TestEvent) event).name())
                    .containsExactly("confirmed");
            return null;
        });
    }

    private record TestEvent(String name) implements DomainEvent {
    }

    private static final class RecordingAggregate implements EventRecordable {

        private final List<DomainEvent> events;
        private int drainCount;

        private RecordingAggregate(DomainEvent event) {
            this.events = new ArrayList<>(List.of(event));
        }

        @Override
        public List<DomainEvent> drainEvents() {
            drainCount++;
            List<DomainEvent> drained = List.copyOf(events);
            events.clear();
            return drained;
        }

        int drainCount() {
            return drainCount;
        }
    }

    private static final class RecordingBeforeCommitDispatcher implements BeforeCommitDomainEventDispatcher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            this.events.addAll(events);
        }
    }

    private static final class RecordingAfterCommitDispatcher implements DomainEventDispatcher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            this.events.addAll(events);
        }
    }

    private static final class RecordingTransactionSynchronizationRegistry
            implements TransactionSynchronizationRegistry {

        private final Map<Object, Object> resources = new IdentityHashMap<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private Object transactionKey;

        private void activate() {
            transactionKey = new Object();
        }

        private void beforeCompletion() {
            synchronizations.forEach(Synchronization::beforeCompletion);
        }

        private void afterCompletion(int status) {
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
            transactionKey = null;
            resources.clear();
            synchronizations.clear();
        }

        @Override
        public Object getTransactionKey() {
            return transactionKey;
        }

        @Override
        public void putResource(Object key, Object value) {
            resources.put(key, value);
        }

        @Override
        public Object getResource(Object key) {
            return resources.get(key);
        }

        @Override
        public int getTransactionStatus() {
            return transactionKey == null ? Status.STATUS_NO_TRANSACTION : Status.STATUS_ACTIVE;
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            synchronizations.add(synchronization);
        }

        @Override
        public void setRollbackOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getRollbackOnly() {
            return false;
        }
    }
}
