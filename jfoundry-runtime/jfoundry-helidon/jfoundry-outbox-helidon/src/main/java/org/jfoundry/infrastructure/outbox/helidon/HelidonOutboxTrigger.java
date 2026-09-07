package org.jfoundry.infrastructure.outbox.helidon;

import io.helidon.scheduling.FixedRate;
import io.helidon.scheduling.Task;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.outbox.OutboxDispatcher;

import java.time.Duration;

/// Helidon scheduling trigger for the framework-neutral Outbox dispatch runtime.
@Dependent
public class HelidonOutboxTrigger {

    private final OutboxDispatcher outboxDispatcher;
    private final boolean enabled;
    private final int batchSize;
    private final Duration interval;
    private Task task;

    @Inject
    public HelidonOutboxTrigger(OutboxDispatcher outboxDispatcher,
                                @ConfigProperty(name = "jfoundry.outbox.dispatcher.enabled", defaultValue = "false")
                                boolean enabled,
                                @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50")
                                int batchSize,
                                @ConfigProperty(name = "jfoundry.outbox.dispatcher.interval", defaultValue = "5s")
                                Duration interval) {
        this.outboxDispatcher = outboxDispatcher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.interval = interval;
    }

    @PostConstruct
    void schedule() {
        if (enabled) {
            task = FixedRate.builder()
                    .delayBy(interval)
                    .interval(interval)
                    .task(ignored -> scheduledDispatch())
                    .build();
        }
    }

    @PreDestroy
    void close() {
        if (task != null) {
            task.close();
        }
    }

    void scheduledDispatch() {
        if (enabled) {
            outboxDispatcher.dispatch(batchSize);
        }
    }
}
