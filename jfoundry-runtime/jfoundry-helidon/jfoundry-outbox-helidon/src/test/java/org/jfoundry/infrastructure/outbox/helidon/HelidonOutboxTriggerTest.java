package org.jfoundry.infrastructure.outbox.helidon;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxTriggerTest {

    @Test
    void scheduledDispatchDelegatesConfiguredBatchSize() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        HelidonOutboxTrigger trigger = new HelidonOutboxTrigger(dispatcher, true, 37, Duration.ofSeconds(5));

        trigger.scheduledDispatch();

        assertThat(dispatcher.lastBatchSize).isEqualTo(37);
    }

    @Test
    void scheduledDispatchDoesNothingWhenDisabled() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        HelidonOutboxTrigger trigger = new HelidonOutboxTrigger(dispatcher, false, 37, Duration.ofSeconds(5));

        trigger.scheduledDispatch();

        assertThat(dispatcher.lastBatchSize).isNull();
    }

    private static final class RecordingDispatcher implements OutboxDispatcher {

        private Integer lastBatchSize;

        @Override
        public void dispatch(int batchSize) {
            lastBatchSize = batchSize;
        }
    }
}
