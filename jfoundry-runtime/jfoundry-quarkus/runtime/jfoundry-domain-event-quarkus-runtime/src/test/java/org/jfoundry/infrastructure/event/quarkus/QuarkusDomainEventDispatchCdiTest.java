package org.jfoundry.infrastructure.event.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class QuarkusDomainEventDispatchCdiTest {

    @Inject
    RecordingDomainEventDispatcher dispatcher;

    @Inject
    OuterApplicationService outerApplicationService;

    @Inject
    FailingApplicationService failingApplicationService;

    @BeforeEach
    void resetDispatcher() {
        dispatcher.reset();
    }

    @Test
    void dispatchesRecordedEventsOnceAtTheOutermostApplicationServiceBoundary() {
        outerApplicationService.handleNested("outer-1", "inner-1");

        assertThat(dispatcher.dispatchCallCount()).isEqualTo(1);
        assertThat(dispatcher.dispatchedEvents())
                .extracting(TestDomainEvent::aggregateId)
                .containsExactly("outer-1", "inner-1");
    }

    @Test
    void discardsRecordedEventsWhenAnApplicationServiceFails() {
        assertThatThrownBy(() -> failingApplicationService.handleAndFail("order-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(dispatcher.dispatchCallCount()).isZero();
        assertThat(dispatcher.dispatchedEvents()).isEmpty();
    }

    @Test
    void rejectsCompletionStageApplicationServiceMethods() {
        assertThatThrownBy(() -> outerApplicationService.handleAsynchronously("order-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("synchronous");

        assertThat(dispatcher.dispatchCallCount()).isZero();
        assertThat(dispatcher.dispatchedEvents()).isEmpty();
    }

    @ApplicationScoped
    static class RecordingDomainEventDispatcher implements DomainEventDispatcher {

        private final List<TestDomainEvent> events = new ArrayList<>();
        private int dispatchCallCount;

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            dispatchCallCount++;
            for (DomainEvent event : events) {
                if (event instanceof TestDomainEvent testDomainEvent) {
                    this.events.add(testDomainEvent);
                }
            }
        }

        int dispatchCallCount() {
            return dispatchCallCount;
        }

        List<TestDomainEvent> dispatchedEvents() {
            return List.copyOf(events);
        }

        void reset() {
            events.clear();
            dispatchCallCount = 0;
        }
    }

    @ApplicationScoped
    @QuarkusDomainEventDispatch
    static class OuterApplicationService {

        @Inject
        DomainEventContext domainEventContext;

        @Inject
        InnerApplicationService innerApplicationService;

        void handleNested(String outerAggregateId, String innerAggregateId) {
            domainEventContext.register(TestAggregate.create(outerAggregateId));
            innerApplicationService.handle(innerAggregateId);
        }

        CompletableFuture<Void> handleAsynchronously(String aggregateId) {
            domainEventContext.register(TestAggregate.create(aggregateId));
            return CompletableFuture.completedFuture(null);
        }
    }

    @ApplicationScoped
    @QuarkusDomainEventDispatch
    static class InnerApplicationService {

        @Inject
        DomainEventContext domainEventContext;

        void handle(String aggregateId) {
            domainEventContext.register(TestAggregate.create(aggregateId));
        }
    }

    @ApplicationScoped
    @QuarkusDomainEventDispatch
    static class FailingApplicationService {

        @Inject
        DomainEventContext domainEventContext;

        void handleAndFail(String aggregateId) {
            domainEventContext.register(TestAggregate.create(aggregateId));
            throw new IllegalStateException("boom");
        }
    }

    static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }

        static TestAggregate create(String aggregateId) {
            TestAggregate aggregate = new TestAggregate(new TestAggregateId(aggregateId));
            aggregate.recordEvent(new TestDomainEvent(aggregateId));
            return aggregate;
        }
    }

    record TestAggregateId(String value) implements Identifier {
    }

    record TestDomainEvent(String aggregateId) implements DomainEvent {
    }
}
