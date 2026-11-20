package io.huahill.jfoundry.autoconfigure.outbox.dispatcher;

import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.outbox.BackoffStrategy;
import io.huahill.jfoundry.application.outbox.DefaultOutboxDispatchService;
import io.huahill.jfoundry.application.outbox.DefaultOutboxMaintenance;
import io.huahill.jfoundry.application.outbox.JdkOutboxWorker;
import io.huahill.jfoundry.application.outbox.JdkOutboxWorkerSettings;
import io.huahill.jfoundry.application.outbox.OutboxDispatcher;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.application.outbox.OutboxRuntimeIds;
import io.huahill.jfoundry.application.transaction.TransactionRunner;
import io.huahill.jfoundry.autoconfigure.transaction.TransactionRunnerAutoConfiguration;
import io.huahill.jfoundry.application.outbox.ExponentialBackoffStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Optional;

/// Auto-configuration for the Outbox dispatcher and JDK worker.
@AutoConfiguration
@AutoConfigureAfter(
        value = TransactionRunnerAutoConfiguration.class,
        name = "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration"
)
@ConditionalOnClass({OutboxMessageStore.class, MessageSender.class, JdkOutboxWorker.class})
@EnableConfigurationProperties(OutboxDispatcherProperties.class)
public class OutboxDispatcherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BackoffStrategy.class)
    public BackoffStrategy exponentialBackoffStrategy(OutboxDispatcherProperties properties) {
        return new ExponentialBackoffStrategy(properties.getBackoffBase(), properties.getBackoffMax());
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, MessageSender.class, BackoffStrategy.class, TransactionRunner.class})
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    public DefaultOutboxDispatchService outboxDispatcher(
            OutboxMessageStore outboxRepository,
            MessageSender messageSender,
            BackoffStrategy backoffStrategy,
            TransactionRunner transactionRunner,
            OutboxDispatcherProperties properties) {
        return new DefaultOutboxDispatchService(
                outboxRepository,
                messageSender,
                transactionRunner,
                properties.getMaxRetries(),
                backoffStrategy,
                OutboxRuntimeIds.generateClaimerId());
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, TransactionRunner.class})
    @ConditionalOnMissingBean(DefaultOutboxMaintenance.class)
    public DefaultOutboxMaintenance outboxMaintenance(
            OutboxMessageStore outboxRepository,
            TransactionRunner transactionRunner) {
        return new DefaultOutboxMaintenance(outboxRepository, transactionRunner);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean({OutboxDispatcher.class, DefaultOutboxMaintenance.class})
    @ConditionalOnMissingBean(JdkOutboxWorker.class)
    @ConditionalOnProperty(prefix = "jfoundry.outbox.dispatcher", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdkOutboxWorker jdkOutboxWorker(
            OutboxDispatcher dispatcher,
            DefaultOutboxMaintenance maintenance,
            Environment environment) {
        return JdkOutboxWorker.start(
                JdkOutboxWorkerSettings.from(environmentLookup(environment)),
                dispatcher,
                maintenance);
    }

    private static JdkOutboxWorkerSettings.ValueLookup environmentLookup(Environment environment) {
        return new JdkOutboxWorkerSettings.ValueLookup() {
            @Override
            public <T> Optional<T> get(String name, Class<T> type) {
                return Optional.ofNullable(environment.getProperty(name, type));
            }
        };
    }
}
