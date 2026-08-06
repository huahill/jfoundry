package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxDispatcherAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(OutboxDispatcherAutoConfiguration.class))
                    .withBean(OutboxMessageStore.class, () -> mock(OutboxMessageStore.class))
                    .withBean(MessageSender.class, () -> outbound -> SendResult.ok())
                    .withBean(BackoffStrategy.class, () -> (BackoffStrategy) failedAttempts -> Duration.ofSeconds(1))
                    .withBean(CountingTransactionRunner.class, CountingTransactionRunner::new);

    private final ApplicationContextRunner runnerWithoutMessageSender =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(OutboxDispatcherAutoConfiguration.class))
                    .withBean(OutboxMessageStore.class, () -> mock(OutboxMessageStore.class))
                    .withBean(BackoffStrategy.class, () -> (BackoffStrategy) failedAttempts -> Duration.ofSeconds(1))
                    .withBean(CountingTransactionRunner.class, CountingTransactionRunner::new);

    @Test
    void scheduledModeRegistersDispatcherAndMaintenanceJobs() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=scheduled")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(OutboxRecoveryJob.class);
                    assertThat(context).hasSingleBean(OutboxCleanupJob.class);
                });
    }

    @Test
    void scheduledModeDoesNotRegisterDispatcherWithoutMessageSender() {
        runnerWithoutMessageSender
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=scheduled")
                .run(context -> assertThat(context).doesNotHaveBean(OutboxDispatcher.class));
    }

    @Test
    void noneModeDoesNotRegisterDispatcherOrMaintenanceJobsByDefault() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=none")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(OutboxRecoveryJob.class);
                    assertThat(context).doesNotHaveBean(OutboxCleanupJob.class);
                });
    }

    @Test
    void jobrunrModeRegistersSpringScheduledMaintenanceJobs() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=jobrunr")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(OutboxRecoveryJob.class);
                    assertThat(context).hasSingleBean(OutboxCleanupJob.class);
                });
    }

    @Test
    void unknownModeFailsFast() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noneModeDoesNotRegisterMaintenanceJobsWhenExplicitlyEnabled() {
        runner
                .withPropertyValues(
                        "jfoundry.outbox.dispatcher.mode=none",
                        "jfoundry.outbox.recovery.enabled=true",
                        "jfoundry.outbox.cleanup.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(OutboxRecoveryJob.class);
                    assertThat(context).doesNotHaveBean(OutboxCleanupJob.class);
                });
    }

    @Test
    void scheduledModeCanDisableMaintenanceJobs() {
        runner
                .withPropertyValues(
                        "jfoundry.outbox.dispatcher.mode=scheduled",
                        "jfoundry.outbox.recovery.enabled=false",
                        "jfoundry.outbox.cleanup.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(OutboxRecoveryJob.class);
                    assertThat(context).doesNotHaveBean(OutboxCleanupJob.class);
                });
    }

    @Test
    void dispatchAndMaintenanceUseTheConfiguredTransactionRunner() {
        runner.run(context -> {
            int callsBeforeInvocation = context.getBean(CountingTransactionRunner.class).calls;
            context.getBean(OutboxDispatcher.class).dispatch(10);
            context.getBean(OutboxRecoveryJob.class).recoverStuckDispatching();
            context.getBean(OutboxCleanupJob.class).runOnce();

            assertThat(context.getBean(CountingTransactionRunner.class).calls)
                    .isGreaterThanOrEqualTo(callsBeforeInvocation + 4);
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
