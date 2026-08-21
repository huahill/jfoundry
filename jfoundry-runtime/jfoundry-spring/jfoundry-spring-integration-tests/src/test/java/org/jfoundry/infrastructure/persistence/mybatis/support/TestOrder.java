package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.AggregateRoot;

import java.time.Instant;

/// Test aggregate root demonstrating the typical pattern where business methods decide which events
/// to record:
/// - {@link #markPaid} has modification semantics and records TestOrderStatusChangedEvent.
/// - {@link #cancel} has modification semantics and records TestOrderStatusChangedEvent.
public class TestOrder extends BaseAggregateRoot<TestOrder, TestOrderId> implements AggregateRoot<TestOrder, TestOrderId> {

    private TestOrderStatus status;
    private int amount;
    private Instant createdAt;
    private Instant updatedAt;

    /// Factory method for creating a new aggregate in assigned-ID mode. Records CreatedEvent during construction.
    public static TestOrder create(TestOrderId id, int amount) {
        TestOrder order = new TestOrder(id);
        order.status = TestOrderStatus.CREATED;
        order.amount = amount;
        Instant now = Instant.now();
        order.createdAt = now;
        order.updatedAt = now;
        order.recordEvent(new TestOrderCreatedEvent(id.value(), now));
        return order;
    }

    /// Persistence reconstruction constructor that restores from Data without recording events.
    public static TestOrder restore(TestOrderId id, TestOrderStatus status, int amount, Instant createdAt, Instant updatedAt) {
        TestOrder order = new TestOrder(id);
        order.status = status;
        order.amount = amount;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        return order;
    }

    private TestOrder(TestOrderId id) {
        super(id);
    }

    /// Business method that marks the order as paid. Has modification semantics and records StatusChangedEvent.
    public void markPaid() {
        transitionTo(TestOrderStatus.PAID);
    }

    /// Business method that cancels the order. Has modification semantics and records StatusChangedEvent.
    public void cancel() {
        transitionTo(TestOrderStatus.CANCELLED);
    }

    private void transitionTo(TestOrderStatus next) {
        TestOrderStatus previous = this.status;
        this.status = next;
        this.updatedAt = Instant.now();
        recordEvent(new TestOrderStatusChangedEvent(getId().value(), previous.name(), next.name(), updatedAt));
    }

    public TestOrderStatus getStatus() {
        return status;
    }

    public int getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
