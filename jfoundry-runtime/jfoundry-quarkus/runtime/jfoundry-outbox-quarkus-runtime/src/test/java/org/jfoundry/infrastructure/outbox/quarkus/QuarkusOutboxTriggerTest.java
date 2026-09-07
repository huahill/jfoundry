package org.jfoundry.infrastructure.outbox.quarkus;

import org.jfoundry.application.outbox.OutboxDispatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusOutboxTriggerTest {

    @Test
    void triggerIsNotAnOutboxDispatcher() {
        assertThat(OutboxDispatcher.class.isAssignableFrom(QuarkusOutboxTrigger.class)).isFalse();
    }

    @Test
    void scheduledDispatchDelegatesTheConfiguredBatchSize() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        QuarkusOutboxTrigger trigger = new QuarkusOutboxTrigger(dispatcher, true, 37);

        trigger.scheduledDispatch();

        assertThat(dispatcher.lastBatchSize).isEqualTo(37);
    }

    @Test
    void scheduledDispatchDoesNothingWhenDisabled() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        QuarkusOutboxTrigger trigger = new QuarkusOutboxTrigger(dispatcher, false, 37);

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
