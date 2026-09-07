package org.jfoundry.infrastructure.outbox.jobrunr.dispatcher;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// JobRunr trigger for the framework-neutral Outbox dispatch runtime.
public class JobRunrOutboxTrigger implements JobRequestHandler<OutboxDispatchJobRequest> {

    private static final Logger log = LoggerFactory.getLogger(JobRunrOutboxTrigger.class);

    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    public JobRunrOutboxTrigger(OutboxDispatcher dispatcher, int batchSize) {
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Job(name = "outbox-dispatch", retries = 3)
    public void recurringDispatch() {
        dispatch();
    }

    @Override
    public void run(OutboxDispatchJobRequest ignored) {
        dispatch();
    }

    private void dispatch() {
        log.debug("[OUTBOX-DISPATCH-JOBRUNR] dispatching batchSize={}", batchSize);
        dispatcher.dispatch(batchSize);
    }
}
