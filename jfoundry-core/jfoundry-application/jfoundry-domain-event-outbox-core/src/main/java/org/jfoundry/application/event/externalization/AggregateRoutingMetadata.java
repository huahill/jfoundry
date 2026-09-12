package org.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;

/// Broker-neutral metadata used for aggregate-scoped routing and ordering.
public record AggregateRoutingMetadata(String aggregateType, String aggregateId, @Nullable Long aggregateVersion) {
}
