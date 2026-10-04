package org.jfoundry.application.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// JDK timer that periodically runs Outbox dispatch, recovery, and cleanup.
///
/// Runtime adapters start and close this worker. They must not substitute a framework
/// scheduler for these ticks.
public final class JdkOutboxWorker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JdkOutboxWorker.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ScheduledExecutorService executor;

    private JdkOutboxWorker(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public static JdkOutboxWorker start(
            JdkOutboxWorkerSettings settings,
            OutboxDispatcher dispatcher,
            DefaultOutboxMaintenance maintenance) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(maintenance, "maintenance");

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(threadFactory());
        JdkOutboxWorker worker = new JdkOutboxWorker(executor);
        worker.schedule(
                "dispatch",
                Duration.ZERO,
                settings.dispatchInterval(),
                () -> dispatcher.dispatch(settings.dispatchBatchSize()));
        worker.schedule(
                "recovery",
                settings.recoveryInterval(),
                settings.recoveryInterval(),
                () -> maintenance.recoverStuckDispatching(settings.recoveryStuckTimeout()));
        worker.schedule(
                "cleanup",
                settings.cleanupInterval(),
                settings.cleanupInterval(),
                () -> maintenance.cleanUpTerminalMessages(
                        settings.publishedRetentionDays(),
                        settings.deadLetteredRetentionDays(),
                        settings.cleanupBatchSize()));
        return worker;
    }

    private void schedule(String name, Duration initialDelay, Duration interval, Runnable task) {
        executor.scheduleWithFixedDelay(
                () -> runSafely(name, task),
                initialDelay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static void runSafely(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception exception) {
            log.error("Outbox {} failed", name, exception);
        }
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "jfoundry-outbox-worker-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("Outbox worker did not terminate");
                }
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
