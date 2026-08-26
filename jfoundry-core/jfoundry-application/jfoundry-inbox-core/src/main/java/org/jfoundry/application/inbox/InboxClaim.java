package org.jfoundry.application.inbox;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// The result of attempting to acquire processing ownership for an Inbox message.
public record InboxClaim(InboxClaimResult result, @Nullable String claimToken, @Nullable InboxClaimSource source) {

    public InboxClaim {
        Objects.requireNonNull(result, "result must not be null");
        if (result == InboxClaimResult.CLAIMED) {
            if (claimToken == null || claimToken.isBlank() || source == null) {
                throw new IllegalArgumentException("processed claim requires a token and source");
            }
        } else if (claimToken != null || source != null) {
            throw new IllegalArgumentException("unclaimed result must not carry ownership metadata");
        }
    }

    public static InboxClaim fresh(String claimToken) {
        return claimed(claimToken, InboxClaimSource.FRESH);
    }

    public static InboxClaim claimed(String claimToken, InboxClaimSource source) {
        return new InboxClaim(InboxClaimResult.CLAIMED, claimToken, source);
    }

    public static InboxClaim duplicate() {
        return new InboxClaim(InboxClaimResult.DUPLICATE, null, null);
    }

    public static InboxClaim inProgress() {
        return new InboxClaim(InboxClaimResult.IN_PROGRESS, null, null);
    }

    public boolean acquired() {
        return result == InboxClaimResult.CLAIMED;
    }

    public InboxExecutionResult executionResult() {
        return switch (result) {
            case DUPLICATE -> InboxExecutionResult.DUPLICATE;
            case IN_PROGRESS -> InboxExecutionResult.IN_PROGRESS;
            case CLAIMED -> throw new IllegalStateException("claimed Inbox lease has no execution result yet");
        };
    }
}
