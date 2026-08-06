package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/// Quarkus scheduled trigger for the framework-neutral Outbox dispatch runtime.
@ApplicationScoped
@DefaultBean
public class QuarkusOutboxDispatcher implements OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(QuarkusOutboxDispatcher.class);

    @Inject
    Instance<OutboxMessageStore> outboxMessageStore;

    @Inject
    Instance<MessageSender> messageSender;

    @Inject
    TransactionRunner transactionRunner;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.max-retries", defaultValue = "5")
    int maxRetries;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-base", defaultValue = "1s")
    Duration backoffBase;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-max", defaultValue = "5m")
    Duration backoffMax;

    @Scheduled(every = "${jfoundry.outbox.dispatcher.interval:5s}", identity = "jfoundry-outbox-dispatcher")
    void scheduledDispatch() {
        if (enabled) {
            dispatch(batchSize);
        }
    }

    @Override
    public void dispatch(int batchSize) {
        if (!outboxMessageStore.isResolvable() || !messageSender.isResolvable()) {
            log.warn("Outbox dispatch requires application beans for OutboxMessageStore and MessageSender");
            return;
        }
        new DefaultOutboxDispatchService(
                outboxMessageStore.get(),
                messageSender.get(),
                transactionRunner,
                maxRetries,
                backoffStrategy(),
                OutboxRuntimeIds.generateClaimerId())
                .dispatch(batchSize);
    }

    private BackoffStrategy backoffStrategy() {
        long baseMillis = backoffBase.toMillis();
        long maximumMillis = backoffMax.toMillis();
        if (baseMillis <= 0 || maximumMillis < baseMillis) {
            throw new IllegalStateException("Invalid Outbox dispatch backoff configuration");
        }
        return failedAttempts -> Duration.ofMillis(Math.min(backoffMillis(baseMillis, failedAttempts), maximumMillis));
    }

    private static long backoffMillis(long baseMillis, int failedAttempts) {
        try {
            return Math.multiplyExact(baseMillis, 1L << Math.max(0, failedAttempts));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
