package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HelidonOutboxDispatchProducerTest {

    @Test
    void producedDispatcherDoesNotResolveDependenciesWhenMissing() {
        HelidonOutboxDispatchProducer producer = new HelidonOutboxDispatchProducer();
        OutboxDispatcher dispatcher = producer.outboxDispatcher(
                unavailableStore(),
                unavailableSender(),
                new NoOpTransactionRunner(),
                5,
                Duration.ZERO,
                Duration.ofSeconds(1));

        assertThatCode(() -> dispatcher.dispatch(11)).doesNotThrowAnyException();
    }

    @Test
    void producedDispatcherDelegatesWithTheConfiguredBatchSize() {
        RecordingOutboxMessageStore store = new RecordingOutboxMessageStore();
        store.messages = List.of(message("evt-1"));
        RecordingTransactionRunner transactionRunner = new RecordingTransactionRunner();
        HelidonOutboxDispatchProducer producer = new HelidonOutboxDispatchProducer();
        OutboxDispatcher dispatcher = producer.outboxDispatcher(
                availableStore(store),
                availableSender(message -> SendResult.ok()),
                transactionRunner,
                5,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5));

        dispatcher.dispatch(37);

        assertThat(store.claimBatchSize).isEqualTo(37);
        assertThat(store.claimerId).isNotBlank();
        assertThat(store.published).containsExactly("evt-1");
        assertThat(transactionRunner.options).hasSize(2);
    }

    @Test
    void producedDispatcherValidatesBackoffWhenDependenciesAreAvailable() {
        HelidonOutboxDispatchProducer producer = new HelidonOutboxDispatchProducer();
        OutboxDispatcher dispatcher = producer.outboxDispatcher(
                availableStore(new RecordingOutboxMessageStore()),
                availableSender(message -> SendResult.ok()),
                new NoOpTransactionRunner(),
                5,
                Duration.ZERO,
                Duration.ofSeconds(1));

        assertThatCode(() -> dispatcher.dispatch(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid Outbox dispatch backoff configuration");
    }

    @SuppressWarnings("unchecked")
    private static Instance<OutboxMessageStore> unavailableStore() {
        return (Instance<OutboxMessageStore>) Proxy.newProxyInstance(
                HelidonOutboxDispatchProducerTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> false;
                    case "isUnsatisfied" -> true;
                    default -> throwUnsupported(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static Instance<MessageSender> unavailableSender() {
        return (Instance<MessageSender>) Proxy.newProxyInstance(
                HelidonOutboxDispatchProducerTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> false;
                    case "isUnsatisfied" -> true;
                    default -> throwUnsupported(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static Instance<OutboxMessageStore> availableStore(OutboxMessageStore store) {
        return (Instance<OutboxMessageStore>) Proxy.newProxyInstance(
                HelidonOutboxDispatchProducerTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> true;
                    case "get" -> store;
                    default -> throwUnsupported(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static Instance<MessageSender> availableSender(MessageSender sender) {
        return (Instance<MessageSender>) Proxy.newProxyInstance(
                HelidonOutboxDispatchProducerTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> true;
                    case "get" -> sender;
                    default -> throwUnsupported(method.getName());
                });
    }

    private static Object throwUnsupported(String method) {
        throw new UnsupportedOperationException(method);
    }

    private static OutboxMessage message(String eventId) {
        return OutboxMessage.newPending(eventId, "topic", "key-" + eventId, "type", "{}", Instant.now());
    }

    private static final class NoOpTransactionRunner implements TransactionRunner {

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
            return callback.execute();
        }
    }

    private static final class RecordingTransactionRunner implements TransactionRunner {

        private final java.util.List<TransactionOptions> options = new java.util.ArrayList<>();

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
            this.options.add(options);
            return callback.execute();
        }
    }

    private static final class RecordingOutboxMessageStore implements OutboxMessageStore {

        private List<OutboxMessage> messages = List.of();
        private int claimBatchSize;
        private String claimerId;
        private final java.util.List<String> published = new java.util.ArrayList<>();

        @Override
        public void append(OutboxMessage message) {
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            return messages.subList(0, Math.min(limit, messages.size()));
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            this.claimBatchSize = limit;
            this.claimerId = claimerId;
            return findDispatchable(limit, Instant.now());
        }

        @Override
        public void markAsPublished(String eventId) {
            published.add(eventId);
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        }

        @Override
        public void reactivate(String eventId) {
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
            return 0;
        }
    }
}
