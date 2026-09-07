package org.jfoundry.infrastructure.outbox.spring.dispatcher;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduledOutboxTriggerTest {

    private OutboxDispatcher dispatcher;
    private ScheduledOutboxTrigger trigger;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OutboxDispatcher.class);
        trigger = new ScheduledOutboxTrigger(dispatcher, 5);
    }

    @Test
    void doesNotImplementOutboxDispatcher() {
        org.junit.jupiter.api.Assertions.assertFalse(
                OutboxDispatcher.class.isAssignableFrom(ScheduledOutboxTrigger.class));
    }

    @Test
    void scheduledDispatchDelegatesConfiguredBatchSize() {
        trigger.scheduledDispatch();

        verify(dispatcher).dispatch(5);
    }
}
