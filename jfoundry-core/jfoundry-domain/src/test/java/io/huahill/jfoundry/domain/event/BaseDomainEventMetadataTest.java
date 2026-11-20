package io.huahill.jfoundry.domain.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseDomainEventMetadataTest {

    @Test
    void occurredAtAndEventIdArePopulatedOnConstruction() {
        Instant before = Instant.now();

        TestEvent event = new TestEvent("agg-1");

        Instant after = Instant.now();

        assertNotNull(event.getOccurredAt());
        assertNotNull(event.getEventId());
        assertTrue(!event.getEventId().toString().isEmpty());
        assertTrue(!event.getOccurredAt().isBefore(before));
        assertTrue(!event.getOccurredAt().isAfter(after));
    }

    @Test
    void eventIdIsUniquePerInstance() {
        TestEvent a = new TestEvent("agg-1");
        TestEvent b = new TestEvent("agg-1");

        assertFalse(a.getEventId().equals(b.getEventId()));
    }

    @Test
    void differentSubclassSharesSameMetadataShape() {
        TestEvent a = new TestEvent("agg-1");
        AnotherEvent b = new AnotherEvent();

        assertEquals(UUID.class, a.getEventId().getClass());
        assertEquals(Instant.class, b.getOccurredAt().getClass());
    }

    @Test
    void equalsAndHashCodeAreBasedOnEventIdForIdempotentDedup() {
        TestEvent a = new TestEvent("agg-1");
        TestEvent b = new TestEvent("agg-1");

        assertFalse(a.equals(b));
        assertEquals(a, a);
        assertEquals(a.hashCode(), a.hashCode());
    }

    static class TestEvent extends BaseDomainEvent {
        private final String aggregateId;

        TestEvent(String aggregateId) {
            super();
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    static class AnotherEvent extends BaseDomainEvent {
    }
}
