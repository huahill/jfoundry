package org.jfoundry.infrastructure.outbox.spring.dispatcher;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.springframework.scheduling.annotation.Scheduled;

/// Spring scheduled adapter that delegates Outbox dispatch to the service port.
public class ScheduledOutboxTrigger {

    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    public ScheduledOutboxTrigger(OutboxDispatcher dispatcher, int batchSize) {
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${jfoundry.outbox.dispatcher.interval-ms:5000}")
    public void scheduledDispatch() {
        dispatcher.dispatch(batchSize);
    }
}
