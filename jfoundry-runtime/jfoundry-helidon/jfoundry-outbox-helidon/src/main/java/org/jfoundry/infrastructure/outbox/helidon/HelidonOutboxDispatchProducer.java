package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
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

/// Produces Outbox services for Helidon and starts the JDK worker.
@Alternative
@Priority(1)
@ApplicationScoped
public final class HelidonOutboxDispatchProducer {

    private JdkOutboxWorker worker;

    @Produces
    OutboxDispatcher outboxDispatcher(Instance<OutboxMessageStore> outboxMessageStore,
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
    DefaultOutboxMaintenance outboxMaintenance(Instance<OutboxMessageStore> outboxMessageStore,
                                               TransactionRunner transactionRunner) {
        return new DefaultOutboxMaintenance(() -> resolve(outboxMessageStore), transactionRunner);
    }

    void startWorker(@Observes @Initialized(ApplicationScoped.class) Object ignored,
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
