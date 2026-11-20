package io.huahill.jfoundry.domain.entity.agg;

import io.huahill.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifiable;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BaseAggregateRootSelfTypeTest {

    @Test
    void aggregateIsJmoleculesAggregateRootAndIdentifiable() {
        Order order = new Order(new OrderId("order-1"));

        assertInstanceOf(AggregateRoot.class, order);
        assertInstanceOf(Identifiable.class, order);
        assertEquals("order-1", order.getId().value());
    }

    @Test
    void selfTypeTokenMatchesConcreteClass() {
        Order order = new Order(new OrderId("order-1"));
        assertEquals(Order.class, order.getClass());
    }

    record OrderId(String value) implements Identifier {
    }

    static class Order extends BaseAggregateRoot<Order, OrderId> {
        Order(OrderId id) {
            super(id);
        }

        void cancel() {
            recordEvent(new OrderCanceledEvent(getId()));
        }
    }

    static class OrderCanceledEvent extends BaseDomainEvent {
        private final OrderId orderId;

        OrderCanceledEvent(OrderId orderId) {
            super();
            this.orderId = orderId;
        }

        public OrderId getOrderId() {
            return orderId;
        }
    }
}
