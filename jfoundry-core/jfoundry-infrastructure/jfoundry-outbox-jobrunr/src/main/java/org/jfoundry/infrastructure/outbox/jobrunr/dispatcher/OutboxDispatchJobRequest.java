package org.jfoundry.infrastructure.outbox.jobrunr.dispatcher;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/// JobRunr request that invokes the framework's recurring Outbox dispatch operation.
public final class OutboxDispatchJobRequest implements JobRequest {

    @Override
    public Class<? extends JobRequestHandler> getJobRequestHandler() {
        return JobRunrOutboxDispatcher.class;
    }
}
