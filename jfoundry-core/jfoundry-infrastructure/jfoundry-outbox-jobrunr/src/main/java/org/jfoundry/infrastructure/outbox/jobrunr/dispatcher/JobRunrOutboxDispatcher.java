package org.jfoundry.infrastructure.outbox.jobrunr.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxRuntimeIds;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// JobRunr trigger for the framework-neutral Outbox dispatch runtime.
public class JobRunrOutboxDispatcher implements OutboxDispatcher, JobRequestHandler<OutboxDispatchJobRequest> {

    private static final Logger log = LoggerFactory.getLogger(JobRunrOutboxDispatcher.class);

    private final OutboxDispatcher dispatchService;
    private final int batchSize;

    public JobRunrOutboxDispatcher(OutboxMessageStore outboxRepository,
                                    MessageSender messageSender,
                                    int batchSize,
                                    int maxRetries,
                                    BackoffStrategy backoff) {
        this(outboxRepository, messageSender, null, batchSize, maxRetries, backoff,
                OutboxRuntimeIds.generateClaimerId());
    }

    public JobRunrOutboxDispatcher(OutboxMessageStore outboxRepository,
                                   MessageSender messageSender,
                                   TransactionRunner transactionRunner,
                                   int batchSize,
                                   int maxRetries,
                                   BackoffStrategy backoff) {
        this(outboxRepository, messageSender, transactionRunner, batchSize, maxRetries, backoff,
                OutboxRuntimeIds.generateClaimerId());
    }

    /// Test-only constructor that allows injecting a podId to assert concurrent mutual exclusion.
    /// The production constructor uses {@link OutboxRuntimeIds#generateClaimerId()}.
    JobRunrOutboxDispatcher(OutboxMessageStore outboxRepository,
                            MessageSender messageSender,
                            int batchSize,
                            int maxRetries,
                            BackoffStrategy backoff,
                            String podId) {
        this(outboxRepository, messageSender, null, batchSize, maxRetries, backoff, podId);
    }

    JobRunrOutboxDispatcher(OutboxMessageStore outboxRepository,
                            MessageSender messageSender,
                            TransactionRunner transactionRunner,
                            int batchSize,
                            int maxRetries,
                            BackoffStrategy backoff,
                            String podId) {
        this.dispatchService = new DefaultOutboxDispatchService(
                outboxRepository, messageSender, transactionRunner, maxRetries, backoff, podId);
        this.batchSize = batchSize;
    }

    @Job(name = "outbox-dispatch", retries = 3)
    public void recurringDispatch() {
        dispatch(batchSize);
    }

    @Override
    public void run(OutboxDispatchJobRequest ignored) {
        recurringDispatch();
    }

    @Override
    public void dispatch(int batchSize) {
        log.debug("[OUTBOX-DISPATCH-JOBRUNR] dispatching batchSize={}", batchSize);
        dispatchService.dispatch(batchSize);
    }
}
