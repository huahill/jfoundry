package org.jfoundry.application.event.externalization;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventExternalizationResolverTest {

    @Test
    void resolvesEveryMessageProducedByMatchingExternalizers() {
        DomainEventExternalizationResolver resolver = new DomainEventExternalizationResolver(List.of(
                externalizer(OrderPlaced.class, List.of(
                        new ExternalizedEvent("orders.v1", "order.created.v1", new OrderCreated("order-1"), "order-1", "Order", "order-1", 3L),
                        new ExternalizedEvent("analytics.v1", "order.analytics.v1", new OrderAnalytics("order-1"), null, null, null, null))),
                externalizer(DomainEvent.class, List.of(
                        new ExternalizedEvent("audit.v1", "order.audit.v1", new OrderAudit("order-1"), "order-1", null, null, null)))));

        List<ExternalizedEvent> events = resolver.resolve(new OrderPlaced()).orElseThrow();

        assertThat(events).containsExactly(
                new ExternalizedEvent("orders.v1", "order.created.v1", new OrderCreated("order-1"), "order-1", "Order", "order-1", 3L),
                new ExternalizedEvent("analytics.v1", "order.analytics.v1", new OrderAnalytics("order-1"), null, null, null, null),
                new ExternalizedEvent("audit.v1", "order.audit.v1", new OrderAudit("order-1"), "order-1", null, null, null));
    }

    @Test
    void returnsEmptyWhenNoExternalizerMatchesTheDomainEvent() {
        DomainEventExternalizationResolver resolver = new DomainEventExternalizationResolver(List.of(
                externalizer(OtherEvent.class, List.of())));

        assertThat(resolver.resolve(new OrderPlaced())).isEmpty();
    }

    @Test
    void retainsMatchedStateWhenAnExternalizerProducesNoMessages() {
        DomainEventExternalizationResolver resolver = new DomainEventExternalizationResolver(List.of(
                externalizer(OrderPlaced.class, List.of())));

        assertThat(resolver.resolve(new OrderPlaced())).hasValue(List.of());
    }

    @Test
    void rejectsMalformedIntegrationMessageMetadata() {
        assertThatThrownBy(() -> new ExternalizedEvent(" ", "order.created.v1", new OrderCreated("order-1"), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> new ExternalizedEvent("orders.v1", " ", new OrderCreated("order-1"), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloadType");
        assertThatThrownBy(() -> new ExternalizedEvent("orders.v1", "order.created.v1", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
        assertThatThrownBy(() -> new ExternalizedEvent("orders.v1", "order.created.v1", new OrderCreated("order-1"), null, "Order", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateType");
        assertThatThrownBy(() -> new ExternalizedEvent("orders.v1", "order.created.v1", new OrderCreated("order-1"), null, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateVersion");
    }

    private static <E extends DomainEvent> DomainEventExternalizer<E> externalizer(
            Class<E> eventType, List<ExternalizedEvent> events) {
        return new DomainEventExternalizer<>() {
            @Override
            public Class<E> sourceEventType() {
                return eventType;
            }

            @Override
            public List<ExternalizedEvent> externalize(E event) {
                return events;
            }
        };
    }

    private record OrderPlaced() implements DomainEvent {
    }

    private record OtherEvent() implements DomainEvent {
    }

    private record OrderCreated(String orderId) {
    }

    private record OrderAnalytics(String orderId) {
    }

    private record OrderAudit(String orderId) {
    }
}
