package org.jfoundry.domain.event;

import org.jspecify.annotations.Nullable;
import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// Base class for domain events.
/// <p>
/// Business events that extend this class receive {@code occurredAt} and
/// {@code eventId} metadata for downstream consumption, audit tracing, and
/// idempotency.
/// <p>
/// This class is immutable: {@code occurredAt} and {@code eventId} are final
/// fields, and subclass business fields should also be final.
/// <p>
/// The authoritative idempotency key is {@link #getEventId()}; consumers should
/// deduplicate by event id instead of object identity. This class also bases
/// equals/hashCode on {@code eventId}, which makes Set/Map deduplication stable.
public abstract class BaseDomainEvent implements DomainEvent {

    /// Time when the event occurred.
    private final Instant occurredAt;

    /// Unique event identifier.
    private final UUID eventId;

    protected BaseDomainEvent() {
        this.occurredAt = Instant.now();
        this.eventId = UUID.randomUUID();
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseDomainEvent that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
