package org.jfoundry.application.inbox;

/// Describes how a message processing lease was obtained.
public enum InboxClaimSource {
    FRESH,
    FAILED_RETRY,
    EXPIRED_LEASE
}
