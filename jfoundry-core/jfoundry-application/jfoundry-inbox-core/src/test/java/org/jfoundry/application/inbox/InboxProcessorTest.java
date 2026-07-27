package org.jfoundry.application.inbox;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxProcessorTest {

    @Test
    void recordsFailureWithClaimTokenAndPropagatesHandlerException() {
        RecordingStore store = new RecordingStore();
        InboxProcessor processor = new InboxProcessor(
                store,
                Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));

        assertThatThrownBy(() -> processor.process("evt-1", "projection", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(store.failedClaimToken).isEqualTo("claim-1");
        assertThat(store.failedMessage).isEqualTo("boom");
    }

    private static final class RecordingStore implements InboxMessageStore {
        private String failedClaimToken;
        private String failedMessage;

        @Override
        public InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration) {
            return InboxClaim.fresh("claim-1");
        }

        @Override
        public boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now) {
            return true;
        }

        @Override
        public boolean markFailed(String messageId, String consumerName, String claimToken, String errorMessage,
                                  Instant now) {
            failedClaimToken = claimToken;
            failedMessage = errorMessage;
            return true;
        }
    }
}
