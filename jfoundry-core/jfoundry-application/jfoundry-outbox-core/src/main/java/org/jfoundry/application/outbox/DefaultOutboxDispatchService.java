package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/// Framework-neutral Outbox dispatch runtime.
/// <p>
/// Framework adapters decide when this service is triggered. This service owns the shared
/// claim/send/mark state transition so Spring, JobRunr, Helidon, or Quarkus integrations do
/// not duplicate delivery behavior.
public class DefaultOutboxDispatchService implements OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutboxDispatchService.class);

    private final OutboxMessageStore repository;
    private final MessageSender messageSender;
    private final int maxRetries;
    private final BackoffStrategy backoff;
    private final String claimerId;
    private final @Nullable TransactionRunner transactionRunner;

    public DefaultOutboxDispatchService(OutboxMessageStore repository,
                                        MessageSender messageSender,
                                        int maxRetries,
                                        BackoffStrategy backoff,
                                        String claimerId) {
        this(repository, messageSender, null, maxRetries, backoff, claimerId);
    }

    /// Creates a dispatcher whose database state transitions run in independent transactions.
    /// Message delivery deliberately remains outside those transactions.
    public DefaultOutboxDispatchService(OutboxMessageStore repository,
                                        MessageSender messageSender,
                                        @Nullable TransactionRunner transactionRunner,
                                        int maxRetries,
                                        BackoffStrategy backoff,
                                        String claimerId) {
        this.repository = repository;
        this.messageSender = messageSender;
        this.transactionRunner = transactionRunner;
        this.maxRetries = maxRetries;
        this.backoff = backoff;
        this.claimerId = claimerId;
    }

    @Override
    public void dispatch(int batchSize) {
        List<OutboxMessage> messages = inNewTransaction(
                () -> repository.claimDispatchable(batchSize, claimerId));
        for (OutboxMessage message : messages) {
            dispatchMessage(message);
        }
    }

    private void dispatchMessage(OutboxMessage message) {
        @Nullable String claimToken = message.getClaimToken();
        try {
            SendResult result = messageSender.send(new OutboundMessage(
                    message.getTopic(), message.getPayloadKey(), message.getPayloadJson(), message.getPropagation()));
            if (result.success()) {
                inNewTransaction(() -> {
                    repository.markAsPublished(message.getEventId(), claimToken);
                    return null;
                });
            } else {
                markAsFailed(message, claimToken, result.errorMessage());
            }
        } catch (RuntimeException e) {
            log.warn("dispatch message {} failed with exception: {}", message.getEventId(), e.getMessage());
            markAsFailed(message, claimToken, e.getMessage());
        }
    }

    private void markAsFailed(OutboxMessage message, @Nullable String claimToken, @Nullable String errorMessage) {
        inNewTransaction(() -> {
            repository.markAsFailed(message.getEventId(), claimToken,
                    errorMessage, maxRetries, backoff);
            return null;
        });
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        if (transactionRunner == null) {
            try {
                return callback.execute();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Outbox operation failed", exception);
            }
        }
        try {
            return transactionRunner.call(TransactionOptions.builder()
                    .propagation(TransactionPropagation.REQUIRES_NEW)
                    .build(), callback);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Outbox transaction failed", exception);
        }
    }
}
