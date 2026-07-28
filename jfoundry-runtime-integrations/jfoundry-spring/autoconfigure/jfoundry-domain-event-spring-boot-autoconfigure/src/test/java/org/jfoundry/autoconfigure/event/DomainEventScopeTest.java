package org.jfoundry.autoconfigure.event;

import org.jfoundry.domain.event.EventRecordable;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventScopeTest {

    private final DomainEventScope scope = new DomainEventScope();

    @Test
    void ignoresRegisteredAggregatesOutsideScope() {
        RecordingAggregate aggregate = new RecordingAggregate(new TestDomainEvent("outside"));

        scope.register(aggregate);

        assertThat(scope.drainEvents()).isEmpty();
        assertThat(aggregate.drainCount()).isZero();
    }

    @Test
    void drainsRegisteredAggregatesInsideScope() throws Throwable {
        RecordingAggregate aggregate = new RecordingAggregate(new TestDomainEvent("inside"));

        List<DomainEvent> events = scope.invoke(outermost -> {
            assertThat(outermost).isTrue();
            scope.register(aggregate);
            return scope.drainEvents();
        });

        assertThat(events)
                .extracting(event -> ((TestDomainEvent) event).name())
                .containsExactly("inside");
        assertThat(aggregate.drainCount()).isOne();
    }

    @Test
    void nestedScopesReuseOuterScope() throws Throwable {
        RecordingAggregate outer = new RecordingAggregate(new TestDomainEvent("outer"));
        RecordingAggregate inner = new RecordingAggregate(new TestDomainEvent("inner"));

        List<DomainEvent> events = scope.invoke(outermost -> {
            assertThat(outermost).isTrue();
            scope.register(outer);
            scope.invoke(nestedOutermost -> {
                assertThat(nestedOutermost).isFalse();
                scope.register(inner);
                return null;
            });
            return scope.drainEvents();
        });

        assertThat(events)
                .extracting(event -> ((TestDomainEvent) event).name())
                .containsExactly("outer", "inner");
    }

    @Test
    void leavesNoBoundScopeAfterFailure() {
        RecordingAggregate failed = new RecordingAggregate(new TestDomainEvent("failed"));
        RecordingAggregate later = new RecordingAggregate(new TestDomainEvent("later"));

        assertThatThrownBy(() -> scope.invoke(outermost -> {
            scope.register(failed);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        scope.register(later);

        assertThat(scope.drainEvents()).isEmpty();
        assertThat(failed.drainCount()).isZero();
        assertThat(later.drainCount()).isZero();
    }

    private static final class RecordingAggregate implements EventRecordable {

        private final List<DomainEvent> events;
        private int drainCount;

        private RecordingAggregate(DomainEvent event) {
            this.events = List.of(event);
        }

        @Override
        public List<DomainEvent> drainEvents() {
            drainCount++;
            return events;
        }

        int drainCount() {
            return drainCount;
        }
    }

    private record TestDomainEvent(String name) implements DomainEvent {
    }
}
