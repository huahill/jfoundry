package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.outbox.OutboxDispatcher;

/// Quarkus scheduled trigger for the framework-neutral Outbox dispatch runtime.
@ApplicationScoped
public final class QuarkusOutboxTrigger {

    private final OutboxDispatcher outboxDispatcher;
    private final boolean enabled;
    private final int batchSize;

    @Inject
    public QuarkusOutboxTrigger(
            OutboxDispatcher outboxDispatcher,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.enabled", defaultValue = "false")
            boolean enabled,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50")
            int batchSize) {
        this.outboxDispatcher = outboxDispatcher;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(every = "${jfoundry.outbox.dispatcher.interval:5s}", identity = "jfoundry-outbox-dispatcher")
    void scheduledDispatch() {
        if (enabled) {
            outboxDispatcher.dispatch(batchSize);
        }
    }
}
