package org.jfoundry.application.inbox;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// Executes Inbox handlers with explicit ownership and transaction boundaries.
public final class InboxTemplate implements InboxMessageProcessor {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);

    private final InboxMessageStore store;
    private final @Nullable TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration leaseDuration;

    public InboxTemplate(InboxMessageStore store) {
        this(store, null, Clock.systemUTC(), DEFAULT_LEASE_DURATION);
    }

    public InboxTemplate(InboxMessageStore store, @Nullable TransactionRunner transactionRunner) {
        this(store, transactionRunner, Clock.systemUTC(), DEFAULT_LEASE_DURATION);
    }

    public InboxTemplate(InboxMessageStore store, @Nullable TransactionRunner transactionRunner, Clock clock,
                         Duration leaseDuration) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transactionRunner = transactionRunner;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
    }

    @Override
    public InboxExecutionResult executeOnce(String messageId, String consumerName, InboxHandler handler) {
        if (transactionRunner == null) {
            return new InboxProcessor(store, clock, leaseDuration).process(messageId, consumerName, handler);
        }
        requireText(messageId, "messageId");
        requireText(consumerName, "consumerName");
        Objects.requireNonNull(handler, "handler must not be null");

        Instant now = clock.instant();
        InboxClaim claim = inNewTransaction(() -> store.claim(messageId, consumerName, now, leaseDuration));
        if (!claim.acquired()) {
            return claim.executionResult();
        }
        String claimToken = Objects.requireNonNull(claim.claimToken(), "acquired claim must have a token");
        try {
            return inNewTransaction(() -> {
                handler.handle();
                if (!store.markProcessed(messageId, consumerName, claimToken, now)) {
                    throw new IllegalStateException("Inbox processing ownership was lost");
                }
                return InboxExecutionResult.PROCESSED;
            });
        } catch (RuntimeException exception) {
            try {
                inNewTransaction(() -> store.markFailed(
                        messageId, consumerName, claimToken, exception.getMessage(), now));
            } catch (RuntimeException recordingException) {
                exception.addSuppressed(recordingException);
            }
            throw exception;
        }
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        try {
            return transactionRunner.call(TransactionOptions.builder()
                    .propagation(TransactionPropagation.REQUIRES_NEW)
                    .build(), callback);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox transaction failed", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
