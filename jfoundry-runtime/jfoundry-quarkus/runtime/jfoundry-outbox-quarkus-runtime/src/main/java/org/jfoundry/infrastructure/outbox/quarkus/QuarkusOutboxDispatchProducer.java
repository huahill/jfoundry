package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.ExponentialBackoffStrategy;
import org.jfoundry.application.outbox.DefaultOutboxMaintenance;
import org.jfoundry.application.outbox.JdkOutboxWorker;
import org.jfoundry.application.outbox.JdkOutboxWorkerSettings;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxRuntimeIds;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/// Produces the default Outbox dispatcher for Quarkus applications and starts the JDK worker.
///
/// Worker schedule keys are injected with {@code @ConfigProperty} so native images keep them.
@ApplicationScoped
public final class QuarkusOutboxDispatchProducer {

    private JdkOutboxWorker worker;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.enabled", defaultValue = "true")
    boolean workerEnabled;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50")
    int dispatchBatchSize;

    @ConfigProperty(name = "jfoundry.outbox.dispatcher.interval", defaultValue = "5s")
    Duration dispatchInterval;

    @ConfigProperty(name = "jfoundry.outbox.recovery.interval", defaultValue = "60s")
    Duration recoveryInterval;

    @ConfigProperty(name = "jfoundry.outbox.recovery.stuck-timeout", defaultValue = "5m")
    Duration recoveryStuckTimeout;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.interval", defaultValue = "24h")
    Duration cleanupInterval;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.published-retention-days", defaultValue = "7")
    int publishedRetentionDays;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.dead-lettered-retention-days", defaultValue = "30")
    int deadLetteredRetentionDays;

    @ConfigProperty(name = "jfoundry.outbox.cleanup.batch-size", defaultValue = "1000")
    int cleanupBatchSize;

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
        ExponentialBackoffStrategy backoff = new ExponentialBackoffStrategy(backoffBase, backoffMax);
        return DefaultOutboxDispatchService.withLazyDependencies(
                () -> resolve(outboxMessageStore),
                () -> resolve(messageSender),
                transactionRunner,
                maxRetries,
                () -> backoff,
                OutboxRuntimeIds.generateClaimerId());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    DefaultOutboxMaintenance outboxMaintenance(
            Instance<OutboxMessageStore> outboxMessageStore,
            TransactionRunner transactionRunner) {
        return new DefaultOutboxMaintenance(() -> resolve(outboxMessageStore), transactionRunner);
    }

    /// Starts the worker after runtime startup. {@code StartupEvent} does not fire during
    /// native image generation, so the timer cannot capture JTA or Hibernate into the image heap.
    void startWorker(@Observes StartupEvent event,
                     OutboxDispatcher dispatcher,
                     DefaultOutboxMaintenance maintenance) {
        synchronized (this) {
            if (worker != null || !workerEnabled) {
                return;
            }
            worker = JdkOutboxWorker.start(workerSettings(), dispatcher, maintenance);
        }
    }

    @PreDestroy
    void stopWorker() {
        if (worker != null) {
            worker.close();
            worker = null;
        }
    }

    private JdkOutboxWorkerSettings workerSettings() {
        return new JdkOutboxWorkerSettings(
                dispatchBatchSize,
                dispatchInterval,
                recoveryInterval,
                recoveryStuckTimeout,
                cleanupInterval,
                publishedRetentionDays,
                deadLetteredRetentionDays,
                cleanupBatchSize);
    }

    private static <T> @Nullable T resolve(Instance<T> instance) {
        return instance.isResolvable() ? instance.get() : null;
    }
}
