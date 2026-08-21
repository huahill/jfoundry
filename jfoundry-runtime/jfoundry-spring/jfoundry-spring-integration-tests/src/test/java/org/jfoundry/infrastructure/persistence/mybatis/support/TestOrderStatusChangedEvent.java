package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;

/// Test domain event for order status changes.
public record TestOrderStatusChangedEvent(String orderId, String fromStatus, String toStatus, Instant occurredAt)
        implements DomainEvent {
}
