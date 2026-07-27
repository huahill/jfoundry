package org.jfoundry.application.inbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// Coordinates lease-owned, idempotent Inbox message processing.
public final class InboxProcessor {

    private final InboxMessageStore store;
    private final Clock clock;
    private final Duration leaseDuration;

    public InboxProcessor(InboxMessageStore store, Clock clock, Duration leaseDuration) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    public InboxExecutionResult process(String messageId, String consumerName, InboxHandler handler) {
        requireText(messageId, "messageId");
        requireText(consumerName, "consumerName");
        Objects.requireNonNull(handler, "handler must not be null");

        Instant now = clock.instant();
        InboxClaim claim = store.claim(messageId, consumerName, now, leaseDuration);
        if (!claim.acquired()) {
            return claim.executionResult();
        }
        try {
            handler.handle();
            if (!store.markProcessed(messageId, consumerName, claim.claimToken(), now)) {
                throw new IllegalStateException("Inbox processing ownership was lost");
            }
            return InboxExecutionResult.PROCESSED;
        } catch (RuntimeException exception) {
            try {
                store.markFailed(messageId, consumerName, claim.claimToken(), exception.getMessage(), now);
            } catch (RuntimeException recordingException) {
                exception.addSuppressed(recordingException);
            }
            throw exception;
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
