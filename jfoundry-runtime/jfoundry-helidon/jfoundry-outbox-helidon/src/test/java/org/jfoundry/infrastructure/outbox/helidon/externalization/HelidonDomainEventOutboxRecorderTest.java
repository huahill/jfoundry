package org.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizer;
import org.jfoundry.application.event.externalization.ExternalizedEvent;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class HelidonDomainEventOutboxRecorderTest {

    @Test
    void ignoresUnmarkedEventsWithoutResolvingTheOutboxStoreOrSerializer() {
        HelidonDomainEventOutboxRecorder recorder = new HelidonDomainEventOutboxRecorder(
                unavailable(OutboxMessageStore.class), unavailable(org.jfoundry.application.messaging.PayloadSerializer.class),
                new ExternalizationRuleResolver(), new org.jfoundry.application.event.externalization.AggregateRoutingResolver());

        assertThatCode(() -> recorder.record(List.of(new InternalEvent()))).doesNotThrowAnyException();
    }

    @Test
    void writesTheMappedIntegrationContractForAnUnannotatedDomainEvent() {
        CapturingOutboxMessageStore store = new CapturingOutboxMessageStore();
        MappedOrderPlaced event = new MappedOrderPlaced();
        HelidonDomainEventOutboxRecorder recorder = new HelidonDomainEventOutboxRecorder(
                available(OutboxMessageStore.class, store),
                available(PayloadSerializer.class, payload -> "serialized:" + payload.getClass().getSimpleName()),
                new ExternalizationRuleResolver(),
                new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of(new MappedOrderPlacedExternalizer())));

        recorder.record(List.of(event));

        assertThat(store.lastAppended.getEventId()).isEqualTo(event.getEventId().toString());
        assertThat(store.lastAppended.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(store.lastAppended.getTopic()).isEqualTo("orders.v1");
        assertThat(store.lastAppended.getPayloadType()).isEqualTo("sales.order-created.v1");
        assertThat(store.lastAppended.getPayloadJson()).isEqualTo("serialized:OrderCreatedV1");
        assertThat(store.lastAppended.getPayloadKey()).isEqualTo("order-3");
        assertThat(store.lastAppended.getAggregateType()).isEqualTo("Order");
        assertThat(store.lastAppended.getAggregateId()).isEqualTo("order-3");
        assertThat(store.lastAppended.getAggregateVersion()).isEqualTo(11L);
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> unavailable(Class<T> type) {
        return (Instance<T>) Proxy.newProxyInstance(
                HelidonDomainEventOutboxRecorderTest.class.getClassLoader(), new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError(type.getSimpleName() + " must not be resolved for an unmarked event");
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> available(Class<T> type, T value) {
        return (Instance<T>) Proxy.newProxyInstance(
                HelidonDomainEventOutboxRecorderTest.class.getClassLoader(), new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> true;
                    case "get" -> value;
                    default -> throw new UnsupportedOperationException(type.getSimpleName() + "." + method.getName());
                });
    }

    private static final class MappedOrderPlaced extends BaseDomainEvent {
    }

    private record OrderCreatedV1(String orderId) {
    }

    private static final class MappedOrderPlacedExternalizer implements DomainEventExternalizer<MappedOrderPlaced> {

        @Override
        public Class<MappedOrderPlaced> sourceEventType() {
            return MappedOrderPlaced.class;
        }

        @Override
        public List<ExternalizedEvent> externalize(MappedOrderPlaced event) {
            return List.of(new ExternalizedEvent(
                    "orders.v1", "sales.order-created.v1", new OrderCreatedV1("order-3"), "order-3",
                    "Order", "order-3", 11L));
        }
    }

    private static final class CapturingOutboxMessageStore implements OutboxMessageStore {

        private OutboxMessage lastAppended;

        @Override
        public void append(OutboxMessage entry) {
            lastAppended = entry;
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            return List.of();
        }

        @Override
        public void markAsPublished(String eventId) {
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        }

        @Override
        public void reactivate(String eventId) {
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            return List.of();
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

    private record InternalEvent() implements DomainEvent {
    }
}
