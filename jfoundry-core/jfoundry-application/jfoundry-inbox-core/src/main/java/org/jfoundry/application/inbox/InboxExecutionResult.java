package org.jfoundry.application.inbox;

/// A successful Inbox invocation result that can be safely acknowledged by a message consumer.
public enum InboxExecutionResult {
    PROCESSED,
    DUPLICATE,
    IN_PROGRESS
}
