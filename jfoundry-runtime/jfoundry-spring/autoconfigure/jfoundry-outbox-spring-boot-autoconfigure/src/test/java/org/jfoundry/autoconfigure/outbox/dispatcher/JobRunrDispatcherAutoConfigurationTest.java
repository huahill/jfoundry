package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// {@link JobRunrDispatcherAutoConfiguration} must be registered through jfoundry-spring-boot-
/// autoconfigure's {@code META-INF/spring/...AutoConfiguration.imports}, and must register
/// {@link JobRunrOutboxDispatcher} as the {@link OutboxDispatcher} bean when {@code mode=jobrunr}.
/// <p>
/// Uses {@link ApplicationContextRunner} + {@link AutoConfigurations#of} instead of
/// {@code @SpringBootTest} to avoid triggering JobRunr's own auto-configuration
/// ({@code BackgroundJobServer} / dashboard), which requires a full DataSource and schema and is
/// unrelated to this test.
class JobRunrDispatcherAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(JobRunrDispatcherAutoConfiguration.class))
                    .withBean(OutboxMessageStore.class, () -> mock(OutboxMessageStore.class))
                    .withBean(MessageSender.class, () -> (MessageSender) outbound -> SendResult.ok())
                    .withBean(BackoffStrategy.class, () -> (BackoffStrategy) failedAttempts -> Duration.ofSeconds(1))
                    .withBean(CountingTransactionRunner.class, CountingTransactionRunner::new);

    @Test
    void jobRunrDispatcherBeanIsRegisteredWhenModeIsJobRunr() {
        runner
                .withPropertyValues(
                        "jfoundry.outbox.dispatcher.mode=jobrunr",
                        "jfoundry.outbox.dispatcher.batchSize=20",
                        "jfoundry.outbox.dispatcher.maxRetries=7"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context.getBean(OutboxDispatcher.class))
                            .isInstanceOf(JobRunrOutboxDispatcher.class);
                    assertThat(context).hasSingleBean(JobRunrOutboxDispatcher.class);
                });
    }

    @Test
    void dispatcherBeanIsAbsentWhenModeIsScheduled() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=scheduled")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(JobRunrOutboxDispatcher.class);
                });
    }

    @Test
    void dispatcherBeanIsAbsentWhenModeIsMissing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
            assertThat(context).doesNotHaveBean(JobRunrOutboxDispatcher.class);
        });
    }

    @Test
    void batchSizeIsInjectedFromProperties() {
        runner
                .withPropertyValues(
                        "jfoundry.outbox.dispatcher.mode=jobrunr",
                        "jfoundry.outbox.dispatcher.batchSize=20",
                        "jfoundry.outbox.dispatcher.maxRetries=7"
                )
                .run(context -> {
                    OutboxMessageStore repo = context.getBean(OutboxMessageStore.class);
                    when(repo.claimDispatchable(anyInt(), any())).thenReturn(List.of());

                    // recurringDispatch uses the batchSize field injected through the constructor
                    // (@Job entrypoint), not the dispatch(int) argument. This is the actual path
                    // where properties injection takes effect.
                    context.getBean(JobRunrOutboxDispatcher.class).recurringDispatch();

                    ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
                    verify(repo).claimDispatchable(captor.capture(), any());
                    assertThat(context.getBean(CountingTransactionRunner.class).calls).isEqualTo(1);
                    assertThat(captor.getValue())
                            .as("batchSize must come from jfoundry.outbox.dispatcher.batchSize=20")
                            .isEqualTo(20);
                });
    }

    @Test
    void schedulesRecurringDispatchUsingAJobRequest() {
        JobRequestScheduler jobRequestScheduler = mock(JobRequestScheduler.class);

        runner
                .withBean(JobRequestScheduler.class, () -> jobRequestScheduler)
                .withPropertyValues(
                        "jfoundry.outbox.dispatcher.mode=jobrunr",
                        "jfoundry.outbox.dispatcher.cron=*/5 * * * * *"
                )
                .run(context -> verify(jobRequestScheduler).scheduleRecurrently(
                        eq("jfoundry-outbox-dispatch"),
                        eq("*/5 * * * * *"),
                        any(org.jobrunr.jobs.lambdas.JobRequest.class)));
    }

    @Test
    void dispatcherIsAbsentWhenOutboxMessageStoreBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JobRunrDispatcherAutoConfiguration.class))
                .withBean(MessageSender.class, () -> (MessageSender) outbound -> SendResult.ok())
                .withBean(BackoffStrategy.class, () -> (BackoffStrategy) failedAttempts -> Duration.ofSeconds(1))
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=jobrunr")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).hasNotFailed();
                });
    }

    static final class CountingTransactionRunner implements TransactionRunner {
        private int calls;

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
            calls++;
            return callback.execute();
        }
    }
}
