package org.jfoundry.infrastructure.event.jta;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JtaDomainEventDispatchSupportTest {

    @Test
    void dispatchesAllDelegatesAfterSuccessfulNonTransactionalInvocation() throws Exception {
        var transactionRegistry = new RecordingTransactionSynchronizationRegistry();
        var scope = new JtaDomainEventScope(transactionRegistry);
        var beforeCommit = new RecordingBeforeCommitDispatcher();
        var afterCommit = new RecordingDispatcher();

        Object result = JtaDomainEventDispatchSupport.invoke(
                scope, List.of(beforeCommit, afterCommit), () -> {
                    scope.register(new RecordingAggregate(new TestEvent("confirmed")));
                    return "result";
                }, ignored -> false, "Test runtime");

        assertThat(result).isEqualTo("result");
        assertThat(beforeCommit.eventNames()).containsExactly("confirmed");
        assertThat(afterCommit.eventNames()).containsExactly("confirmed");
    }

    @Test
    void dispatchesOnlyBeforeCommitDelegatesDuringTransactionalInvocation() throws Exception {
        var transactionRegistry = new RecordingTransactionSynchronizationRegistry();
        transactionRegistry.activate();
        var scope = new JtaDomainEventScope(transactionRegistry);
        var beforeCommit = new RecordingBeforeCommitDispatcher();
        var afterCommit = new RecordingDispatcher();

        JtaDomainEventDispatchSupport.invoke(
                scope, List.of(beforeCommit, afterCommit), () -> {
                    scope.register(new RecordingAggregate(new TestEvent("confirmed")));
                    return null;
                }, ignored -> false, "Test runtime");

        assertThat(beforeCommit.eventNames()).containsExactly("confirmed");
        assertThat(afterCommit.events).isEmpty();

        transactionRegistry.afterCompletion(Status.STATUS_COMMITTED);

        assertThat(afterCommit.eventNames()).containsExactly("confirmed");
    }

    @Test
    void marksTransactionalInvocationAsFailedAndSuppressesDispatch() {
        var transactionRegistry = new RecordingTransactionSynchronizationRegistry();
        transactionRegistry.activate();
        var scope = new JtaDomainEventScope(transactionRegistry);
        var beforeCommit = new RecordingBeforeCommitDispatcher();
        var afterCommit = new RecordingDispatcher();

        assertThatThrownBy(() -> JtaDomainEventDispatchSupport.invoke(
                scope, List.of(beforeCommit, afterCommit), () -> {
                    scope.register(new RecordingAggregate(new TestEvent("failed")));
                    throw new IOException("write failed");
                }, ignored -> false, "Test runtime"))
                .isInstanceOf(IOException.class)
                .hasMessage("write failed");

        transactionRegistry.afterCompletion(Status.STATUS_COMMITTED);

        assertThat(beforeCommit.events).isEmpty();
        assertThat(afterCommit.events).isEmpty();
    }

    @Test
    void rejectsAsynchronousResultsAndSuppressesDispatch() {
        var transactionRegistry = new RecordingTransactionSynchronizationRegistry();
        var scope = new JtaDomainEventScope(transactionRegistry);
        var dispatcher = new RecordingDispatcher();

        assertThatThrownBy(() -> JtaDomainEventDispatchSupport.invoke(
                scope, List.of(dispatcher), () -> {
                    scope.register(new RecordingAggregate(new TestEvent("deferred")));
                    return new Object();
                }, ignored -> true, "Test runtime"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Test runtime domain-event dispatch supports synchronous application-service methods only");

        assertThat(dispatcher.events).isEmpty();
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

    private static class RecordingDispatcher implements DomainEventDispatcher {

        final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            this.events.addAll(events);
        }

        List<String> eventNames() {
            return events.stream().map(event -> ((TestEvent) event).name()).toList();
        }
    }

    private static final class RecordingBeforeCommitDispatcher extends RecordingDispatcher
            implements BeforeCommitDomainEventDispatcher {
    }

    private static final class RecordingTransactionSynchronizationRegistry
            implements TransactionSynchronizationRegistry {

        private final Map<Object, Object> resources = new IdentityHashMap<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private Object transactionKey;

        private void activate() {
            transactionKey = new Object();
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
