package org.jfoundry.application.inbox;

/// Processes one message delivery with Inbox idempotency and lease ownership.
@FunctionalInterface
public interface InboxMessageProcessor {

    /// Processes a delivery once and returns an acknowledgement-safe result.
    InboxExecutionResult executeOnce(String messageId, String consumerName, InboxHandler handler);
}
