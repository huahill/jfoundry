package io.huahill.jfoundry.domain.entity.agg;

import io.huahill.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAggregateRootTest {

    @Test
    void aggregateRootStoresFrameworkIndependentDomainEvents() {
        TestAggregateRoot aggregateRoot = new TestAggregateRoot(new TestAggregateId("root-1"));
        TestDomainEvent event = new TestDomainEvent(new TestAggregateId("root-1"));

        aggregateRoot.raise(event);

        var drainedEvents = aggregateRoot.drainEvents();

        assertEquals(1, drainedEvents.size());
        assertEquals(event, drainedEvents.getFirst());
        assertTrue(aggregateRoot.drainEvents().isEmpty());
    }

    @Test
    void drainEventsReturnsSnapshotThatIsUnaffectedByLaterEvents() {
        TestAggregateRoot aggregateRoot = new TestAggregateRoot(new TestAggregateId("root-1"));
        TestDomainEvent firstEvent = new TestDomainEvent(new TestAggregateId("root-1"));
        TestDomainEvent secondEvent = new TestDomainEvent(new TestAggregateId("root-1"));
        TestDomainEvent laterEvent = new TestDomainEvent(new TestAggregateId("root-1"));
        aggregateRoot.raise(firstEvent);
        aggregateRoot.raise(secondEvent);

        var drainedEvents = aggregateRoot.drainEvents();
        aggregateRoot.raise(laterEvent);
        var laterDrainedEvents = aggregateRoot.drainEvents();

        assertEquals(2, drainedEvents.size());
        assertEquals(firstEvent, drainedEvents.get(0));
        assertEquals(secondEvent, drainedEvents.get(1));
        assertEquals(1, laterDrainedEvents.size());
        assertEquals(laterEvent, laterDrainedEvents.getFirst());
        assertTrue(aggregateRoot.drainEvents().isEmpty());
    }

    @Test
    void recordDomainEventRejectsNullEvent() {
        TestAggregateRoot aggregateRoot = new TestAggregateRoot(new TestAggregateId("root-1"));

        assertThrows(IllegalArgumentException.class, () -> aggregateRoot.raise(null));
        assertTrue(aggregateRoot.drainEvents().isEmpty());
    }

    record TestAggregateId(String value) implements Identifier {
    }

    private static class TestAggregateRoot extends BaseAggregateRoot<TestAggregateRoot, TestAggregateId> {
        private TestAggregateRoot(TestAggregateId id) {
            super(id);
        }

        private void raise(DomainEvent event) {
            recordEvent(event);
        }
    }

    private static class TestDomainEvent extends BaseDomainEvent {
        private final TestAggregateId aggregateId;

        TestDomainEvent(TestAggregateId aggregateId) {
            super();
            this.aggregateId = aggregateId;
        }

        public TestAggregateId getAggregateId() {
            return aggregateId;
        }
    }
}
