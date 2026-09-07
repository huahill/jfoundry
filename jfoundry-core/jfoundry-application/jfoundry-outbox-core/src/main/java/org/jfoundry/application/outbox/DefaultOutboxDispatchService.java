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
import java.util.function.Supplier;

/// Framework-neutral Outbox dispatch service implementation.
/// <p>
/// Runtime adapters decide when this service is triggered. This service owns the shared
/// claim/send/mark state transition so Spring, JobRunr, Helidon, or Quarkus integrations do
/// not duplicate delivery behavior.
public class DefaultOutboxDispatchService implements OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutboxDispatchService.class);

    private final Supplier<@Nullable OutboxMessageStore> repositorySupplier;
    private final Supplier<@Nullable MessageSender> messageSenderSupplier;
    private final int maxRetries;
    private final Supplier<BackoffStrategy> backoffSupplier;
    private final String claimerId;
    private final @Nullable TransactionRunner transactionRunner;

    public DefaultOutboxDispatchService(OutboxMessageStore repository,
                                        MessageSender messageSender,
                                        int maxRetries,
                                        BackoffStrategy backoff,
                                        String claimerId) {
        this(() -> repository, () -> messageSender, null, maxRetries, () -> backoff, claimerId);
    }

    /// Creates a dispatcher whose database state transitions run in independent transactions.
    /// Message delivery deliberately remains outside those transactions.
    public DefaultOutboxDispatchService(OutboxMessageStore repository,
                                        MessageSender messageSender,
                                        @Nullable TransactionRunner transactionRunner,
                                        int maxRetries,
                                        BackoffStrategy backoff,
                                        String claimerId) {
        this(() -> repository, () -> messageSender, transactionRunner, maxRetries, () -> backoff, claimerId);
    }

    private DefaultOutboxDispatchService(Supplier<@Nullable OutboxMessageStore> repositorySupplier,
                                         Supplier<@Nullable MessageSender> messageSenderSupplier,
                                         @Nullable TransactionRunner transactionRunner,
                                         int maxRetries,
                                         Supplier<BackoffStrategy> backoffSupplier,
                                         String claimerId) {
        this.repositorySupplier = repositorySupplier;
        this.messageSenderSupplier = messageSenderSupplier;
        this.transactionRunner = transactionRunner;
        this.maxRetries = maxRetries;
        this.backoffSupplier = backoffSupplier;
        this.claimerId = claimerId;
    }

    /// Creates a dispatcher whose store, sender, and backoff strategy are resolved at dispatch time.
    /// Missing store or sender dependencies skip the run without failing construction.
    public static DefaultOutboxDispatchService withLazyDependencies(
            Supplier<@Nullable OutboxMessageStore> repositorySupplier,
            Supplier<@Nullable MessageSender> messageSenderSupplier,
            @Nullable TransactionRunner transactionRunner,
            int maxRetries,
            Supplier<BackoffStrategy> backoffSupplier,
            String claimerId) {
        return new DefaultOutboxDispatchService(
                repositorySupplier, messageSenderSupplier, transactionRunner, maxRetries, backoffSupplier, claimerId);
    }

    @Override
    public void dispatch(int batchSize) {
        OutboxMessageStore repository = repositorySupplier.get();
        MessageSender messageSender = messageSenderSupplier.get();
        if (repository == null || messageSender == null) {
            log.warn("Outbox dispatch requires application beans for OutboxMessageStore and MessageSender");
            return;
        }
        BackoffStrategy backoff = backoffSupplier.get();
        List<OutboxMessage> messages = inNewTransaction(
                () -> repository.claimDispatchable(batchSize, claimerId));
        for (OutboxMessage message : messages) {
            dispatchMessage(message, repository, messageSender, backoff);
        }
    }

    private void dispatchMessage(OutboxMessage message,
                                 OutboxMessageStore repository,
                                 MessageSender messageSender,
                                 BackoffStrategy backoff) {
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
                markAsFailed(message, repository, claimToken, result.errorMessage(), backoff);
            }
        } catch (RuntimeException e) {
            log.warn("dispatch message {} failed with exception: {}", message.getEventId(), e.getMessage());
            markAsFailed(message, repository, claimToken, e.getMessage(), backoff);
        }
    }

    private void markAsFailed(OutboxMessage message,
                              OutboxMessageStore repository,
                              @Nullable String claimToken,
                              @Nullable String errorMessage,
                              BackoffStrategy backoff) {
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
