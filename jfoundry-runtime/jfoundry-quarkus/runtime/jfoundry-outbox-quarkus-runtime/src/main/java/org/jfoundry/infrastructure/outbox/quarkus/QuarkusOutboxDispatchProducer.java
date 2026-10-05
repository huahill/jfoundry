package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;
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
@ApplicationScoped
public final class QuarkusOutboxDispatchProducer {

    private JdkOutboxWorker worker;

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
                     DefaultOutboxMaintenance maintenance,
                     Config config) {
        synchronized (this) {
            if (worker == null && JdkOutboxWorkerSettings.workerEnabled(config::getOptionalValue)) {
                worker = JdkOutboxWorker.start(
                        JdkOutboxWorkerSettings.from(config::getOptionalValue), dispatcher, maintenance);
            }
        }
    }

    @PreDestroy
    void stopWorker() {
        if (worker != null) {
            worker.close();
            worker = null;
        }
    }

    private static <T> @Nullable T resolve(Instance<T> instance) {
        return instance.isResolvable() ? instance.get() : null;
    }
}
