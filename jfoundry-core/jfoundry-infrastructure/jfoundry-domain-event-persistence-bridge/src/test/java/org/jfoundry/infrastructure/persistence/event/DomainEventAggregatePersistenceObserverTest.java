package org.jfoundry.infrastructure.persistence.event;

import org.jfoundry.application.event.DefaultDomainEventContext;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventAggregatePersistenceObserverTest {

    @Test
    void registersEventRecordingAggregates() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();
        EventAggregate aggregate = new EventAggregate(new EventId("event"));

        new DomainEventAggregatePersistenceObserver(context).afterPersisted(aggregate);

        assertThat(context.drainRegistered()).containsExactly(aggregate);
    }

    @Test
    void ignoresAggregatesThatDoNotRecordEvents() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();
        PlainAggregate aggregate = new PlainAggregate(new PlainId("plain"));

        new DomainEventAggregatePersistenceObserver(context).afterPersisted(aggregate);

        assertThat(context.drainRegistered()).isEmpty();
    }

    private static final class EventAggregate extends BaseAggregateRoot<EventAggregate, EventId> {

        private EventAggregate(EventId id) {
            super(id);
        }
    }

    private record PlainAggregate(PlainId id) implements AggregateRoot<PlainAggregate, PlainId> {

        @Override
        public PlainId getId() {
            return id;
        }
    }

    private record EventId(String value) implements Identifier, Serializable {
    }

    private record PlainId(String value) implements Identifier, Serializable {
    }
}
