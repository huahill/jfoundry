package org.jfoundry.application.event.externalization;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateRoutingResolverTest {

    private final AggregateRoutingResolver resolver = new AggregateRoutingResolver();

    @AggregateRouting(type = "Order", id = "orderId", version = "version")
    static class OrderEvent implements DomainEvent {
        public String getOrderId() {
            return "order-1";
        }

        public long getVersion() {
            return 7L;
        }
    }

    @AggregateRouting(id = "#this.order.id", version = "#this.order.version")
    static class NestedOrderEvent implements DomainEvent {
        public Order getOrder() {
            return new Order("order-2", 8L);
        }
    }

    static class NotAggregateRouted implements DomainEvent {
    }

    record Order(String id, long version) {
    }

    @Test
    void resolvesAggregateRoutingMetadata() {
        AggregateRoutingMetadata metadata = resolver.resolve(new OrderEvent()).orElseThrow();

        assertThat(metadata.aggregateType()).isEqualTo("Order");
        assertThat(metadata.aggregateId()).isEqualTo("order-1");
        assertThat(metadata.aggregateVersion()).isEqualTo(7L);
    }

    @Test
    void defaultsAggregateTypeToEventSimpleName() {
        AggregateRoutingMetadata metadata = resolver.resolve(new NestedOrderEvent()).orElseThrow();

        assertThat(metadata.aggregateType()).isEqualTo("NestedOrderEvent");
        assertThat(metadata.aggregateId()).isEqualTo("order-2");
        assertThat(metadata.aggregateVersion()).isEqualTo(8L);
    }

    @Test
    void notAnnotatedReturnsEmpty() {
        assertThat(resolver.resolve(new NotAggregateRouted())).isEmpty();
    }
}
