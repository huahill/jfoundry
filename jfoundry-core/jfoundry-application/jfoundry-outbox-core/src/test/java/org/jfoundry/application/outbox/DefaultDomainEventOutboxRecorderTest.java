package org.jfoundry.application.outbox;

import org.jfoundry.application.event.externalization.AggregateRouting;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizer;
import org.jfoundry.application.event.externalization.ExternalizedEvent;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.event.externalization.MessageRouting;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.event.annotation.Externalized;
import org.jmolecules.event.types.DomainEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDomainEventOutboxRecorderTest {

    private final CapturingOutboxMessageStore repository = new CapturingOutboxMessageStore();
    private final DefaultDomainEventOutboxRecorder outboxRecorder = new DefaultDomainEventOutboxRecorder(
            repository,
            event -> "{}",
            new ExternalizationRuleResolver(),
            new AggregateRoutingResolver());

    @Externalized("order.created")
    @AggregateRouting(type = "Order", id = "orderId", version = "version")
    static class OrderCreatedEvent implements DomainEvent {
        public String getOrderId() {
            return "order-1";
        }

        public long getVersion() {
            return 7L;
        }
    }

    @Externalized("order.created")
    @MessageRouting(topic = "order.created", key = "tenantId")
    @AggregateRouting(type = "Order", id = "orderId")
    static class ExplicitKeyEvent implements DomainEvent {
        public String getTenantId() {
            return "tenant-1";
        }

        public String getOrderId() {
            return "order-2";
        }
    }

    @Test
    void storesAggregateMetadataAndUsesAggregateIdAsFallbackPayloadKey() {
        outboxRecorder.record(List.of(new OrderCreatedEvent()));

        OutboxMessage entry = repository.lastAppended();
        assertThat(entry.getTopic()).isEqualTo("order.created");
        assertThat(entry.getPayloadType()).isEqualTo("order.created");
        assertThat(entry.getPayloadKey()).isEqualTo("order-1");
        assertThat(entry.getAggregateType()).isEqualTo("Order");
        assertThat(entry.getAggregateId()).isEqualTo("order-1");
        assertThat(entry.getAggregateVersion()).isEqualTo(7L);
    }

    @Test
    void explicitPayloadKeyWinsOverAggregateId() {
        outboxRecorder.record(List.of(new ExplicitKeyEvent()));

        OutboxMessage entry = repository.lastAppended();
        assertThat(entry.getPayloadType()).isEqualTo("order.created");
        assertThat(entry.getPayloadKey()).isEqualTo("tenant-1");
        assertThat(entry.getAggregateId()).isEqualTo("order-2");
    }

    @Test
    void writesTheMappedIntegrationContractForAnUnannotatedDomainEvent() {
        MappedOrderPlaced event = new MappedOrderPlaced();
        DefaultDomainEventOutboxRecorder recorder = recorder(new MappedOrderPlacedExternalizer());

        recorder.record(List.of(event));

        OutboxMessage entry = repository.lastAppended();
        assertThat(entry.getEventId()).isEqualTo(MappedOutboxEventIds.derive(
                event.getEventId().toString(), "sales.order-created.v1", "orders.v1"));
        assertThat(entry.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(entry.getTopic()).isEqualTo("orders.v1");
        assertThat(entry.getPayloadType()).isEqualTo("sales.order-created.v1");
        assertThat(entry.getPayloadJson()).isEqualTo("serialized:OrderCreatedV1");
        assertThat(entry.getPayloadKey()).isEqualTo("order-3");
        assertThat(entry.getAggregateType()).isEqualTo("Order");
        assertThat(entry.getAggregateId()).isEqualTo("order-3");
        assertThat(entry.getAggregateVersion()).isEqualTo(11L);
    }

    @Test
    void writesOneOutboxRowPerMappedDestination() {
        MappedOrderPlaced event = new MappedOrderPlaced();
        DefaultDomainEventOutboxRecorder recorder = recorder(new MultiDestinationExternalizer());

        recorder.record(List.of(event));

        assertThat(repository.appended()).extracting(OutboxMessage::getTopic)
                .containsExactly("orders.v1", "analytics.v1");
        assertThat(repository.appended()).extracting(OutboxMessage::getPayloadType)
                .containsExactly("sales.order-created.v1", "analytics.order-placed.v1");
        assertThat(repository.appended()).extracting(OutboxMessage::getEventId).containsExactly(
                MappedOutboxEventIds.derive(event.getEventId().toString(), "sales.order-created.v1", "orders.v1"),
                MappedOutboxEventIds.derive(event.getEventId().toString(), "analytics.order-placed.v1", "analytics.v1"));
        assertThat(repository.appended()).extracting(OutboxMessage::getOccurredAt)
                .containsOnly(event.getOccurredAt());
    }

    @Test
    void writesNothingWhenTheMappedDestinationListIsEmpty() {
        DefaultDomainEventOutboxRecorder recorder = recorder(new EmptyMappedExternalizer());

        recorder.record(List.of(new MappedOrderPlaced()));

        assertThat(repository.appended()).isEmpty();
    }

    @Test
    void rejectsDuplicateMappedDestinationsBeforeAppending() {
        DefaultDomainEventOutboxRecorder recorder = recorder(new DuplicateDestinationExternalizer());

        assertThatThrownBy(() -> recorder.record(List.of(new MappedOrderPlaced())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mapped Outbox destinations must be unique per source event: payloadType=sales.order-created.v1, topic=orders.v1");
        assertThat(repository.appended()).isEmpty();
    }

    @Test
    void allowsTheSameMappedDestinationForDifferentSourceEvents() {
        DefaultDomainEventOutboxRecorder recorder = recorder(new MappedOrderPlacedExternalizer());
        MappedOrderPlaced first = new MappedOrderPlaced();
        MappedOrderPlaced second = new MappedOrderPlaced();

        recorder.record(List.of(first, second));

        assertThat(repository.appended()).hasSize(2);
        assertThat(repository.appended()).extracting(OutboxMessage::getEventId).containsExactly(
                MappedOutboxEventIds.derive(first.getEventId().toString(), "sales.order-created.v1", "orders.v1"),
                MappedOutboxEventIds.derive(second.getEventId().toString(), "sales.order-created.v1", "orders.v1"));
        assertThat(repository.appended()).extracting(OutboxMessage::getPayloadType)
                .containsOnly("sales.order-created.v1");
    }

    @Test
    void ignoresUnmarkedEventsWithoutResolvingTheOutboxStoreOrSerializer() {
        AtomicInteger storeResolutions = new AtomicInteger();
        AtomicInteger serializerResolutions = new AtomicInteger();
        DefaultDomainEventOutboxRecorder recorder = new DefaultDomainEventOutboxRecorder(
                countingStore(storeResolutions),
                countingSerializer(serializerResolutions),
                new ExternalizationRuleResolver(),
                new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of()));

        recorder.record(List.of(new InternalEvent()));

        assertThat(storeResolutions).hasValue(0);
        assertThat(serializerResolutions).hasValue(0);
        assertThat(repository.appended()).isEmpty();
    }

    @Test
    void doesNotResolveLazyDependenciesForAnEmptyMappedList() {
        AtomicInteger storeResolutions = new AtomicInteger();
        AtomicInteger serializerResolutions = new AtomicInteger();
        DefaultDomainEventOutboxRecorder recorder = new DefaultDomainEventOutboxRecorder(
                countingStore(storeResolutions),
                countingSerializer(serializerResolutions),
                new ExternalizationRuleResolver(),
                new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of(new EmptyMappedExternalizer())));

        recorder.record(List.of(new MappedOrderPlaced()));

        assertThat(storeResolutions).hasValue(0);
        assertThat(serializerResolutions).hasValue(0);
        assertThat(repository.appended()).isEmpty();
    }

    @Test
    void resolvesLazyDependenciesOncePerRecordCallWhenAppending() {
        AtomicInteger storeResolutions = new AtomicInteger();
        AtomicInteger serializerResolutions = new AtomicInteger();
        DefaultDomainEventOutboxRecorder recorder = new DefaultDomainEventOutboxRecorder(
                countingStore(storeResolutions),
                countingSerializer(serializerResolutions),
                new ExternalizationRuleResolver(),
                new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of(new MultiDestinationExternalizer())));

        recorder.record(List.of(new MappedOrderPlaced(), new OrderCreatedEvent()));

        assertThat(storeResolutions).hasValue(1);
        assertThat(serializerResolutions).hasValue(1);
        assertThat(repository.appended()).hasSize(3);
    }

    @Test
    void rejectsNullEventListsAndNullEvents() {
        assertThatThrownBy(() -> outboxRecorder.record(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain events must not be null.");
        assertThatThrownBy(() -> outboxRecorder.record(java.util.Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain event must not be null.");
        assertThat(repository.appended()).isEmpty();
    }

    private DefaultDomainEventOutboxRecorder recorder(DomainEventExternalizer<?> externalizer) {
        return new DefaultDomainEventOutboxRecorder(
                repository,
                payload -> "serialized:" + payload.getClass().getSimpleName(),
                new ExternalizationRuleResolver(),
                new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of(externalizer)));
    }

    private Supplier<OutboxMessageStore> countingStore(AtomicInteger resolutions) {
        return () -> {
            resolutions.incrementAndGet();
            return repository;
        };
    }

    private static Supplier<PayloadSerializer> countingSerializer(AtomicInteger resolutions) {
        return () -> {
            resolutions.incrementAndGet();
            return payload -> "serialized:" + payload.getClass().getSimpleName();
        };
    }

    static final class MappedOrderPlaced extends BaseDomainEvent {
    }

    record OrderCreatedV1(String orderId) {
    }

    record OrderPlacedAnalyticsV1(String orderId) {
    }

    record InternalEvent() implements DomainEvent {
    }

    static final class MappedOrderPlacedExternalizer implements DomainEventExternalizer<MappedOrderPlaced> {

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

    static final class MultiDestinationExternalizer implements DomainEventExternalizer<MappedOrderPlaced> {

        @Override
        public Class<MappedOrderPlaced> sourceEventType() {
            return MappedOrderPlaced.class;
        }

        @Override
        public List<ExternalizedEvent> externalize(MappedOrderPlaced event) {
            return List.of(
                    new ExternalizedEvent(
                            "orders.v1", "sales.order-created.v1", new OrderCreatedV1("order-3"), "order-3",
                            "Order", "order-3", 11L),
                    new ExternalizedEvent(
                            "analytics.v1", "analytics.order-placed.v1", new OrderPlacedAnalyticsV1("order-3"),
                            "order-3", "Order", "order-3", 11L));
        }
    }

    static final class EmptyMappedExternalizer implements DomainEventExternalizer<MappedOrderPlaced> {

        @Override
        public Class<MappedOrderPlaced> sourceEventType() {
            return MappedOrderPlaced.class;
        }

        @Override
        public List<ExternalizedEvent> externalize(MappedOrderPlaced event) {
            return List.of();
        }
    }

    static final class DuplicateDestinationExternalizer implements DomainEventExternalizer<MappedOrderPlaced> {

        @Override
        public Class<MappedOrderPlaced> sourceEventType() {
            return MappedOrderPlaced.class;
        }

        @Override
        public List<ExternalizedEvent> externalize(MappedOrderPlaced event) {
            ExternalizedEvent destination = new ExternalizedEvent(
                    "orders.v1", "sales.order-created.v1", new OrderCreatedV1("order-3"), "order-3",
                    "Order", "order-3", 11L);
            return List.of(destination, destination);
        }
    }

    static final class CapturingOutboxMessageStore implements OutboxMessageStore {
        private final List<OutboxMessage> appended = new ArrayList<>();

        @Override
        public void append(OutboxMessage entry) {
            appended.add(entry);
        }

        List<OutboxMessage> appended() {
            return List.copyOf(appended);
        }

        OutboxMessage lastAppended() {
            return appended.getLast();
        }

        @Override
        public void markAsPublished(String eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAsFailed(String eventId, @Nullable String errorMessage, int maxRetries, BackoffStrategy backoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reactivate(String eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
            throw new UnsupportedOperationException();
        }
    }
}
