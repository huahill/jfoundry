package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxRuntimeIds;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/// Produces the default Outbox dispatcher for Quarkus applications.
@ApplicationScoped
public final class QuarkusOutboxDispatchProducer {

    @Produces
    @DefaultBean
    @ApplicationScoped
    OutboxDispatcher outboxDispatcher(
            Instance<OutboxMessageStore> outboxMessageStore,
            Instance<MessageSender> messageSender,
            TransactionRunner transactionRunner,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.max-retries", defaultValue = "5")
            int maxRetries,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-base", defaultValue = "1s")
            Duration backoffBase,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.backoff-max", defaultValue = "5m")
            Duration backoffMax) {
        return DefaultOutboxDispatchService.withLazyDependencies(
                () -> resolve(outboxMessageStore),
                () -> resolve(messageSender),
                transactionRunner,
                maxRetries,
                () -> backoffStrategy(backoffBase, backoffMax),
                OutboxRuntimeIds.generateClaimerId());
    }

    private static <T> @Nullable T resolve(Instance<T> instance) {
        return instance.isResolvable() ? instance.get() : null;
    }

    private static BackoffStrategy backoffStrategy(Duration base, Duration maximum) {
        long baseMillis = base.toMillis();
        long maximumMillis = maximum.toMillis();
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
