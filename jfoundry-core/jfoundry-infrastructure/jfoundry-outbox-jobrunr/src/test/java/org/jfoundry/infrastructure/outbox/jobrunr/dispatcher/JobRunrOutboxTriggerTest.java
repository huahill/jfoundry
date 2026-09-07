package org.jfoundry.infrastructure.outbox.jobrunr.dispatcher;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobRunrOutboxTriggerTest {

    private static final int BATCH_SIZE = 50;

    private OutboxDispatcher dispatcher;
    private JobRunrOutboxTrigger trigger;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OutboxDispatcher.class);
        trigger = new JobRunrOutboxTrigger(dispatcher, BATCH_SIZE);
    }

    @Test
    void triggerIsNotAnOutboxDispatcher() {
        assertThat(trigger).isNotInstanceOf(OutboxDispatcher.class);
    }

    @Test
    void recurringDispatchDelegatesWithConfiguredBatchSize() {
        trigger.recurringDispatch();

        verify(dispatcher).dispatch(BATCH_SIZE);
    }

    @Test
    void runDelegatesWithConfiguredBatchSize() {
        trigger.run(new OutboxDispatchJobRequest());

        verify(dispatcher).dispatch(BATCH_SIZE);
    }
}
