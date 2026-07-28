package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.autoconfigure.transaction.TransactionRunnerAutoConfiguration;
import org.jfoundry.infrastructure.outbox.spring.backoff.ExponentialBackoffStrategy;
import org.jfoundry.infrastructure.outbox.spring.dispatcher.ScheduledOutboxDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/// Auto-configuration for the Outbox Dispatcher.
/// <p>
/// Selects the Outbox dispatch trigger according to {@code jfoundry.outbox.dispatcher.mode}:
/// <ul>
///   <li>{@code scheduled} (default): registers ScheduledOutboxDispatcher. This class enables
///       scheduling.</li>
///   <li>{@code jobrunr}: requires jfoundry-outbox-jobrunr on the classpath. The Spring Boot starter
///       registers {@code JobRunrDispatcherAutoConfiguration} through this auto-configuration module.
///       It is mutually exclusive with this class: both sides are guarded by
///       {@code @ConditionalOnMissingBean(OutboxDispatcher.class)}, and their modes match
///       {@code scheduled} and {@code jobrunr} respectively, so they cannot both apply. Recovery and
///       cleanup remain lightweight Spring scheduled maintenance jobs.</li>
///   <li>{@code none}: registers no dispatcher, recovery job, or cleanup job.</li>
/// </ul>
@AutoConfiguration
@AutoConfigureAfter(
        value = TransactionRunnerAutoConfiguration.class,
        name = "org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration"
)
@ConditionalOnClass({OutboxMessageStore.class, MessageSender.class, ScheduledOutboxDispatcher.class})
@EnableConfigurationProperties({OutboxDispatcherProperties.class, OutboxRecoveryProperties.class, OutboxCleanupProperties.class})
public class OutboxDispatcherAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(OutboxMaintenanceConditions.SchedulingEnabled.class)
    @EnableScheduling
    static class SchedulingConfiguration {
    }

    @Bean
    @ConditionalOnMissingBean(BackoffStrategy.class)
    public BackoffStrategy exponentialBackoffStrategy(OutboxDispatcherProperties properties) {
        return new ExponentialBackoffStrategy(properties.getBackoffBaseMs(), properties.getBackoffMaxMs());
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, MessageSender.class, BackoffStrategy.class, TransactionRunner.class})
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    @ConditionalOnProperty(prefix = "jfoundry.outbox.dispatcher", name = "mode", havingValue = "scheduled", matchIfMissing = true)
    public ScheduledOutboxDispatcher scheduledOutboxDispatcher(
            OutboxMessageStore outboxRepository,
            MessageSender messageSender,
            BackoffStrategy backoffStrategy,
            TransactionRunner transactionRunner,
            OutboxDispatcherProperties properties) {
        return new ScheduledOutboxDispatcher(outboxRepository, messageSender, transactionRunner,
                properties.getMaxRetries(), backoffStrategy, properties.getBatchSize());
    }

    /// Stuck-DISPATCHING recovery job.
    /// <p>
    /// Registered only when {@link OutboxMessageStore} exists and recovery is enabled. Recovery is
    /// enabled by default for {@code scheduled} and {@code jobrunr} dispatching, and is disabled
    /// when {@code mode=none}.
    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, TransactionRunner.class})
    @ConditionalOnMissingBean(OutboxRecoveryJob.class)
    @Conditional(OutboxMaintenanceConditions.RecoveryEnabled.class)
    public OutboxRecoveryJob outboxRecoveryJob(OutboxMessageStore outboxRepository,
                                               OutboxRecoveryProperties recoveryProperties,
                                               TransactionRunner transactionRunner) {
        return new OutboxRecoveryJob(outboxRepository, recoveryProperties, transactionRunner);
    }

    /// Terminal-state cleanup job.
    /// <p>
    /// Registered only when {@link OutboxMessageStore} exists and cleanup is enabled. Cleanup is
    /// enabled by default for {@code scheduled} and {@code jobrunr} dispatching, and is disabled
    /// when {@code mode=none}.
    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, TransactionRunner.class})
    @ConditionalOnMissingBean(OutboxCleanupJob.class)
    @Conditional(OutboxMaintenanceConditions.CleanupEnabled.class)
    public OutboxCleanupJob outboxCleanupJob(OutboxMessageStore outboxRepository,
                                             OutboxCleanupProperties cleanupProperties,
                                             TransactionRunner transactionRunner) {
        return new OutboxCleanupJob(outboxRepository, cleanupProperties, transactionRunner);
    }
}
