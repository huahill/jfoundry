package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;

/// Test domain event for order creation.
public record TestOrderCreatedEvent(String orderId, Instant occurredAt) implements DomainEvent {
}
