package org.jfoundry.application.outbox;

/// Records an explicit integration message in an Outbox.
@FunctionalInterface
public interface OutboxRecorder {

    /// Serializes and appends one pending Outbox message in the caller's transaction.
    void append(OutboxAppendRequest request);
}
