package org.jfoundry.infrastructure.event.spring.dispatcher;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringApplicationEventDispatcherTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishImmediatelyWhenNoTransactionSynchronizationActive() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent event = new TestDomainEvent("order-1");

        dispatcher.dispatch(List.of(event));

        assertEquals(List.of(event), applicationEventPublisher.publishedEvents());
    }

    @Test
    void deferPublishUntilTransactionCommitWhenSynchronizationActive() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent event = new TestDomainEvent("order-1");

        TransactionSynchronizationManager.initSynchronization();
        dispatcher.dispatch(List.of(event));

        assertTrue(applicationEventPublisher.publishedEvents().isEmpty());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertEquals(List.of(event), applicationEventPublisher.publishedEvents());
    }

    @Test
    void doNotPublishWhenTransactionRollsBack() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent event = new TestDomainEvent("order-1");

        TransactionSynchronizationManager.initSynchronization();
        dispatcher.dispatch(List.of(event));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertTrue(applicationEventPublisher.publishedEvents().isEmpty());
    }

    @Test
    void publishBatchImmediatelyWhenNoTransactionSynchronizationActive() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent first = new TestDomainEvent("order-1");
        TestDomainEvent second = new TestDomainEvent("order-2");

        dispatcher.dispatch(List.of(first, second));

        assertEquals(List.of(first, second), applicationEventPublisher.publishedEvents());
    }

    @Test
    void publishRegistersSingleTransactionSynchronizationForBatch() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent first = new TestDomainEvent("order-1");
        TestDomainEvent second = new TestDomainEvent("order-2");

        TransactionSynchronizationManager.initSynchronization();
        dispatcher.dispatch(List.of(first, second));

        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        assertTrue(applicationEventPublisher.publishedEvents().isEmpty());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertEquals(List.of(first, second), applicationEventPublisher.publishedEvents());
    }

    @Test
    void publishDoesNothingForEmptyArguments() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);

        TransactionSynchronizationManager.initSynchronization();
        dispatcher.dispatch(List.of());

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        assertTrue(applicationEventPublisher.publishedEvents().isEmpty());
    }

    @Test
    void rejectNullEventList() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch(null));

        assertEquals("Domain events must not be null.", exception.getMessage());
    }

    @Test
    void rejectNullEventElement() {
        RecordingApplicationEventPublisher applicationEventPublisher = new RecordingApplicationEventPublisher();
        SpringApplicationEventDispatcher dispatcher = new SpringApplicationEventDispatcher(applicationEventPublisher);
        TestDomainEvent event = new TestDomainEvent("order-1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch(Arrays.asList(event, null)));

        assertEquals("Domain event must not be null.", exception.getMessage());
        assertTrue(applicationEventPublisher.publishedEvents().isEmpty());
    }

    private record TestDomainEvent(String aggregateId) implements DomainEvent {
    }

    private static class RecordingApplicationEventPublisher implements ApplicationEventPublisher {
        private final List<Object> publishedEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            publishedEvents.add(event);
        }

        private List<Object> publishedEvents() {
            return publishedEvents;
        }
    }
}
