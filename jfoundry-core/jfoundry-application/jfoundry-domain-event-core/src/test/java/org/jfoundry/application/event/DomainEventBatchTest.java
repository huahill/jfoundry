package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventBatchTest {

    @Test
    void copiesAndValidatesEvents() {
        TestDomainEvent event = new TestDomainEvent("order-1");

        List<DomainEvent> eventBatch = DomainEventBatch.copyAndValidate(List.of(event));

        assertThat(eventBatch).containsExactly(event);
    }

    @Test
    void rejectsNullEventList() {
        assertThatThrownBy(() -> DomainEventBatch.copyAndValidate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain events must not be null.");
    }

    @Test
    void rejectsNullEventElement() {
        assertThatThrownBy(() -> DomainEventBatch.copyAndValidate(Arrays.asList(new TestDomainEvent("order-1"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Domain event must not be null.");
    }

    record TestDomainEvent(String aggregateId) implements DomainEvent {
    }
}
