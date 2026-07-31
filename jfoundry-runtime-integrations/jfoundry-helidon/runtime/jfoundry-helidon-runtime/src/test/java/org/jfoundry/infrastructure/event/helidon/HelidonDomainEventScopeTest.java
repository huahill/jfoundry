package org.jfoundry.infrastructure.event.helidon;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonDomainEventScopeTest {

    @Test
    void makesOutboxEventsAvailableBeforeTransactionCompletionAndDispatchesLocalEventsAfterCommit() throws Exception {
        RecordingTransactionSynchronizationRegistry transactionRegistry =
                new RecordingTransactionSynchronizationRegistry();
        transactionRegistry.activate();
        HelidonDomainEventScope scope = new HelidonDomainEventScope(transactionRegistry);
        RecordingBeforeCommitDispatcher outbox = new RecordingBeforeCommitDispatcher();
        RecordingAfterCommitDispatcher local = new RecordingAfterCommitDispatcher();

        scope.invoke(List.of(outbox, local), outermost -> {
            scope.register(new RecordingAggregate(new TestEvent("confirmed")));

            outbox.dispatch(scope.drainEvents());
            assertThat(outbox.events).extracting(event -> ((TestEvent) event).name())
                    .containsExactly("confirmed");
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

    @Test
    void drainsRegisteredAggregateEventsOnlyAtTheOutermostScope() throws Exception {
        HelidonDomainEventScope scope = new HelidonDomainEventScope(
                new RecordingTransactionSynchronizationRegistry());
        TestAggregate aggregate = new TestAggregate();
        aggregate.record(new TestEvent("confirmed"));

        List<DomainEvent> events = scope.invoke(outermost -> {
            scope.register(aggregate);
            return outermost ? scope.drainEvents() : List.of();
        });

        assertThat(events).extracting(event -> ((TestEvent) event).name()).containsExactly("confirmed");
    }

    private static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate() {
            super(new TestAggregateId("order-1"));
        }

        private void record(DomainEvent event) {
            recordEvent(event);
        }
    }

    private record TestAggregateId(String value) implements Identifier {
    }

    private record TestEvent(String name) implements DomainEvent {
    }

    private static final class RecordingAggregate implements EventRecordable {

        private final List<DomainEvent> events;

        private RecordingAggregate(DomainEvent event) {
            this.events = new ArrayList<>(List.of(event));
        }

        @Override
        public List<DomainEvent> drainEvents() {
            List<DomainEvent> drained = List.copyOf(events);
            events.clear();
            return drained;
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
