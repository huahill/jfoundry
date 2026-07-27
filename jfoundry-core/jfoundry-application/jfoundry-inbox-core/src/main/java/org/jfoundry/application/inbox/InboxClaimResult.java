package org.jfoundry.application.inbox;

/// The outcome of a lease-acquisition attempt for an Inbox message.
public enum InboxClaimResult {
    CLAIMED,
    DUPLICATE,
    IN_PROGRESS
}
