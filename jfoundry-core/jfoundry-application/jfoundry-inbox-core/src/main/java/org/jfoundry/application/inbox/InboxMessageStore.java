package org.jfoundry.application.inbox;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/// Persistence contract for lease-owned Inbox message processing.
public interface InboxMessageStore {

    InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration);

    boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now);

    boolean markFailed(String messageId, String consumerName, String claimToken,
                       @Nullable String errorMessage, Instant now);
}
