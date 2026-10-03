package org.jfoundry.infrastructure.outbox.jobrunr.quarkus;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QuarkusJobRunrOutboxProducerTest {

    @Test
    void createsTheJobRunrHandlerAndSchedulesTheRecurringDispatch() {
        OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
        JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
        QuarkusJobRunrOutboxProducer producer = new QuarkusJobRunrOutboxProducer("*/10 * * * * *", 25);

        JobRunrOutboxTrigger trigger = producer.jobRunrOutboxTrigger(dispatcher);
        producer.registerRecurringJob(scheduler);
        trigger.run(new OutboxDispatchJobRequest());

        verify(dispatcher).dispatch(25);
        verify(scheduler).scheduleRecurrently(
                eq(QuarkusJobRunrOutboxProducer.RECURRING_JOB_ID),
                eq("*/10 * * * * *"),
                any(OutboxDispatchJobRequest.class));
        assertThat(trigger).isInstanceOf(JobRunrOutboxTrigger.class);
    }
}
