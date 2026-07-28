package org.jfoundry.application.event;

import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDomainEventContextTest {

    @Test
    void registeredAggregateIsReturnedOnceInRegistrationOrder() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();
        TestRecordable first = new TestRecordable("first");
        TestRecordable second = new TestRecordable("second");

        context.register(first);
        context.register(first);
        context.register(second);

        assertThat(context.drainRegistered()).containsExactly(first, second);
        assertThat(context.drainRegistered()).isEmpty();
    }

    @Test
    void distinctButEqualsEqualAggregatesAreRegisteredIndependently() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();
        EqualRecordable first = new EqualRecordable("same");
        EqualRecordable second = new EqualRecordable("same");

        context.register(first);
        context.register(second);

        assertThat(context.drainRegistered()).containsExactly(first, second);
    }

    @Test
    void registerRejectsNullAggregate() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();

        assertThatThrownBy(() -> context.register(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aggregate must not be null");
    }

    @Test
    void drainRegisteredReturnsEmptyWhenNothingWasRegistered() {
        DefaultDomainEventContext context = new DefaultDomainEventContext();

        assertThat(context.drainRegistered()).isEmpty();
    }

    private record TestRecordable(String id) implements EventRecordable {

        @Override
        public List<DomainEvent> drainEvents() {
            return List.of();
        }
    }

    private static final class EqualRecordable implements EventRecordable {

        private final String id;

        private EqualRecordable(String id) {
            this.id = id;
        }

        @Override
        public List<DomainEvent> drainEvents() {
            return List.of();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EqualRecordable other)) {
                return false;
            }
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
