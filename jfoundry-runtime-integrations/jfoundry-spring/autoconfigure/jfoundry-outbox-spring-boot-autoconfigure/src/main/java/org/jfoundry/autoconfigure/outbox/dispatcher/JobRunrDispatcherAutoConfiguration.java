package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.autoconfigure.transaction.TransactionRunnerAutoConfiguration;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxDispatcher;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Auto-configuration for the JobRunr Outbox Dispatcher.
/// <p>
/// When {@code jfoundry-outbox-jobrunr} is on the classpath and
/// {@code jfoundry.outbox.dispatcher.mode=jobrunr}, this class automatically registers a
/// {@link JobRunrOutboxDispatcher} bean, overriding the {@code scheduled}-mode
/// {@code ScheduledOutboxDispatcher}. The latter is registered by
/// {@code OutboxDispatcherAutoConfiguration} when mode is scheduled or missing; mutual exclusion is
/// based on the OutboxDispatcher bean.
/// <p>
/// Applications do not need to component-scan {@code org.jfoundry.infrastructure.outbox.jobrunr};
/// the Spring Boot starter registers this configuration through
/// {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} in
/// jfoundry-spring-boot-autoconfigure.
/// <p>
/// batchSize, maxRetries, and cron are all read from {@link OutboxDispatcherProperties}, matching
/// scheduled-mode behavior with the same {@code jfoundry.outbox.dispatcher.*} configuration.
@AutoConfiguration
@AutoConfigureAfter(TransactionRunnerAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.jobrunr.jobs.annotations.Job",
        "org.jobrunr.scheduling.JobScheduler",
        "org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxDispatcher"
})
@ConditionalOnProperty(prefix = "jfoundry.outbox.dispatcher", name = "mode", havingValue = "jobrunr")
@EnableConfigurationProperties(OutboxDispatcherProperties.class)
public class JobRunrDispatcherAutoConfiguration {

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, MessageSender.class, BackoffStrategy.class, TransactionRunner.class})
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    public JobRunrOutboxDispatcher jobRunrOutboxDispatcher(
            OutboxMessageStore outboxRepository,
            MessageSender messageSender,
            BackoffStrategy backoffStrategy,
            TransactionRunner transactionRunner,
            OutboxDispatcherProperties properties,
            ObjectProvider<JobScheduler> jobScheduler) {
        JobRunrOutboxDispatcher dispatcher = new JobRunrOutboxDispatcher(
                outboxRepository,
                messageSender,
                transactionRunner,
                properties.getBatchSize(),
                properties.getMaxRetries(),
                backoffStrategy);
        jobScheduler.ifAvailable(scheduler -> scheduler.scheduleRecurrently(
                "jfoundry-outbox-dispatch",
                properties.getCron(),
                dispatcher::recurringDispatch));
        return dispatcher;
    }
}
