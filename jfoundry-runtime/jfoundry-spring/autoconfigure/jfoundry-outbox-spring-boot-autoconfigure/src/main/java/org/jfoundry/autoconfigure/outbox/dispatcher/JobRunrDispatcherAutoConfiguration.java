package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.autoconfigure.transaction.TransactionRunnerAutoConfiguration;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

/// Auto-configuration for the JobRunr Outbox Dispatcher.
/// <p>
/// When {@code jfoundry-outbox-jobrunr} is on the classpath and
/// {@code jfoundry.outbox.dispatcher.mode=jobrunr}, this class automatically registers a
/// {@link JobRunrOutboxTrigger} bean alongside the default
/// {@link DefaultOutboxDispatchService}. The scheduled trigger is registered by
/// {@code OutboxDispatcherAutoConfiguration} only in scheduled mode.
/// <p>
/// Applications do not need to component-scan {@code org.jfoundry.infrastructure.outbox.jobrunr};
/// the Spring Boot starter registers this configuration through
/// {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} in
/// jfoundry-outbox-spring-boot-autoconfigure.
/// <p>
/// batchSize, maxRetries, and cron are all read from {@link OutboxDispatcherProperties}, matching
/// scheduled-mode behavior with the same {@code jfoundry.outbox.dispatcher.*} configuration.
@AutoConfiguration
@AutoConfigureAfter({
        TransactionRunnerAutoConfiguration.class,
        OutboxDispatcherAutoConfiguration.class
})
@ConditionalOnClass(name = {
        "org.jobrunr.jobs.annotations.Job",
        "org.jobrunr.scheduling.JobScheduler",
        "org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger"
})
@ConditionalOnProperty(prefix = "jfoundry.outbox.dispatcher", name = "mode", havingValue = "jobrunr")
@EnableConfigurationProperties(OutboxDispatcherProperties.class)
@ImportRuntimeHints(JobRunrNativeRuntimeHints.class)
public class JobRunrDispatcherAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxDispatcher.class)
    @ConditionalOnMissingBean(JobRunrOutboxTrigger.class)
    public JobRunrOutboxTrigger jobRunrOutboxTrigger(
            OutboxDispatcher dispatcher,
            OutboxDispatcherProperties properties,
            ObjectProvider<JobRequestScheduler> jobRequestScheduler) {
        jobRequestScheduler.ifAvailable(scheduler -> scheduler.scheduleRecurrently(
                "jfoundry-outbox-dispatch",
                properties.getCron(),
                new OutboxDispatchJobRequest()));
        return new JobRunrOutboxTrigger(dispatcher, properties.getBatchSize());
    }
}
