package org.jfoundry.infrastructure.outbox.spring.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxRuntimeIds;
import org.jfoundry.application.transaction.TransactionRunner;
import org.springframework.scheduling.annotation.Scheduled;

/// Spring scheduled trigger for the framework-neutral Outbox dispatch runtime.
public class ScheduledOutboxDispatcher implements OutboxDispatcher {

    private final OutboxDispatcher dispatchService;
    private final int batchSize;

    public ScheduledOutboxDispatcher(OutboxMessageStore repository,
                                      MessageSender messageSender,
                                      int maxRetries,
                                      BackoffStrategy backoff,
                                      int batchSize) {
        this(repository, messageSender, null, maxRetries, backoff, batchSize, OutboxRuntimeIds.generateClaimerId());
    }

    public ScheduledOutboxDispatcher(OutboxMessageStore repository,
                                     MessageSender messageSender,
                                     TransactionRunner transactionRunner,
                                     int maxRetries,
                                     BackoffStrategy backoff,
                                     int batchSize) {
        this(repository, messageSender, transactionRunner, maxRetries, backoff, batchSize,
                OutboxRuntimeIds.generateClaimerId());
    }

    /// Test-only constructor that allows injecting a podId to assert concurrent mutual exclusion.
    /// The production constructor uses {@link OutboxRuntimeIds#generateClaimerId()}.
    public ScheduledOutboxDispatcher(OutboxMessageStore repository,
                                     MessageSender messageSender,
                                     int maxRetries,
                                     BackoffStrategy backoff,
                                     int batchSize,
                                     String podId) {
        this(repository, messageSender, null, maxRetries, backoff, batchSize, podId);
    }

    ScheduledOutboxDispatcher(OutboxMessageStore repository,
                              MessageSender messageSender,
                              TransactionRunner transactionRunner,
                              int maxRetries,
                              BackoffStrategy backoff,
                              int batchSize,
                              String podId) {
        this.dispatchService = new DefaultOutboxDispatchService(
                repository, messageSender, transactionRunner, maxRetries, backoff, podId);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${jfoundry.outbox.dispatcher.interval-ms:5000}")
    public void scheduledDispatch() {
        dispatch(batchSize);
    }

    @Override
    public void dispatch(int batchSize) {
        dispatchService.dispatch(batchSize);
    }
}
