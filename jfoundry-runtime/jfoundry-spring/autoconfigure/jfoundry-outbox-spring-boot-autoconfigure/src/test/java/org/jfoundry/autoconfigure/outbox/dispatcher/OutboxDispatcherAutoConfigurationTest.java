package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.application.outbox.DefaultOutboxMaintenance;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.application.outbox.JdkOutboxWorker;
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
    void enabledWorkerRegistersDispatcherMaintenanceAndWorker() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DefaultOutboxDispatchService.class);
            assertThat(context.getBean(OutboxDispatcher.class))
                    .isInstanceOf(DefaultOutboxDispatchService.class);
            assertThat(context).hasSingleBean(DefaultOutboxMaintenance.class);
            assertThat(context).hasSingleBean(JdkOutboxWorker.class);
        });
    }

    @Test
    void dispatcherIsNotRegisteredWithoutMessageSender() {
        runnerWithoutMessageSender.run(context -> {
            assertThat(context).doesNotHaveBean(DefaultOutboxDispatchService.class);
            assertThat(context).doesNotHaveBean(JdkOutboxWorker.class);
        });
    }

    @Test
    void userProvidedDispatcherBacksOffTheDefaultService() {
        runner
                .withBean(CountingDispatcher.class, CountingDispatcher::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DefaultOutboxDispatchService.class);
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(JdkOutboxWorker.class);
                    context.getBean(OutboxDispatcher.class).dispatch(13);
                    assertThat(context.getBean(CountingDispatcher.class).dispatchCalls).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void disabledWorkerKeepsDispatcherAndMaintenance() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxDispatcher.class);
                    assertThat(context).hasSingleBean(DefaultOutboxMaintenance.class);
                    assertThat(context).doesNotHaveBean(JdkOutboxWorker.class);
                });
    }

    @Test
    void dispatchAndMaintenanceUseTheConfiguredTransactionRunner() {
        runner
                .withPropertyValues("jfoundry.outbox.dispatcher.enabled=false")
                .run(context -> {
                    int callsBeforeInvocation = context.getBean(CountingTransactionRunner.class).calls;
                    context.getBean(OutboxDispatcher.class).dispatch(10);
                    context.getBean(DefaultOutboxMaintenance.class).recoverStuckDispatching(Duration.ofMinutes(5));
                    context.getBean(DefaultOutboxMaintenance.class).cleanUpTerminalMessages(7, 30, 1000);

                    assertThat(context.getBean(CountingTransactionRunner.class).calls)
                            .isGreaterThanOrEqualTo(callsBeforeInvocation + 4);
                });
    }

    static final class CountingTransactionRunner implements TransactionRunner {
        private int calls;

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) {
            calls++;
            return callback.execute();
        }
    }

    static final class CountingDispatcher implements OutboxDispatcher {
        private int dispatchCalls;

        @Override
        public void dispatch(int batchSize) {
            this.dispatchCalls++;
        }
    }
}
