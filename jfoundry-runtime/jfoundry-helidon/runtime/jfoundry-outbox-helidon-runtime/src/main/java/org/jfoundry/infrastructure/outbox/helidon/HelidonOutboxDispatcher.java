package org.jfoundry.infrastructure.outbox.helidon;

import io.helidon.scheduling.Scheduling;
import io.helidon.scheduling.Task;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxRuntimeIds;
import org.jfoundry.application.transaction.TransactionRunner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/// Helidon scheduling trigger for the framework-neutral Outbox dispatch runtime.
@Dependent
public class HelidonOutboxDispatcher implements OutboxDispatcher {

    private final Instance<OutboxMessageStore> outboxMessageStore;
    private final Instance<MessageSender> messageSender;
    private final TransactionRunner transactionRunner;
    private final boolean enabled;
    private final int batchSize;
    private final int maxRetries;
    private final Duration backoffBase;
    private final Duration backoffMax;
    private final Duration interval;
    private Task task;

    @Inject
    public HelidonOutboxDispatcher(Instance<OutboxMessageStore> outboxMessageStore, Instance<MessageSender> messageSender,
                                   TransactionRunner transactionRunner,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.enabled", defaultValue = "false") boolean enabled,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50") int batchSize,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.max-retries", defaultValue = "5") int maxRetries,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-base", defaultValue = "1s") Duration backoffBase,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-max", defaultValue = "5m") Duration backoffMax,
                                   @ConfigProperty(name = "jfoundry.outbox.dispatcher.interval", defaultValue = "5s") Duration interval) {
        this.outboxMessageStore = outboxMessageStore;
        this.messageSender = messageSender;
        this.transactionRunner = transactionRunner;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.backoffBase = backoffBase;
        this.backoffMax = backoffMax;
        this.interval = interval;
    }

    @PostConstruct
    void schedule() {
        if (enabled) task = Scheduling.fixedRateBuilder().initialDelay(interval.toMillis()).delay(interval.toMillis())
                .timeUnit(TimeUnit.MILLISECONDS).task(ignored -> dispatch(batchSize)).build();
    }

    @PreDestroy
    void close() { if (task != null) task.close(); }

    @Override
    public void dispatch(int requestedBatchSize) {
        if (!outboxMessageStore.isResolvable() || !messageSender.isResolvable()) return;
        new DefaultOutboxDispatchService(outboxMessageStore.get(), messageSender.get(), transactionRunner, maxRetries,
                failedAttempts -> Duration.ofMillis(Math.min(backoffBase.toMillis() * (1L << Math.max(0, failedAttempts)), backoffMax.toMillis())),
                OutboxRuntimeIds.generateClaimerId()).dispatch(requestedBatchSize);
    }
}
