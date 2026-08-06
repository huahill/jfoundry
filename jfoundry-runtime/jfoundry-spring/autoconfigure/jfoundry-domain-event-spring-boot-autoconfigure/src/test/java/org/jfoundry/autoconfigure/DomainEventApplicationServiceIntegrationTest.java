package org.jfoundry.autoconfigure;

import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = DomainEventApplicationServiceIntegrationTest.TestApp.class)
class DomainEventApplicationServiceIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        RecordingDomainEventDispatcher recordingDomainEventDispatcher() {
            return new RecordingDomainEventDispatcher();
        }

        @Bean
        TestAggregateRepository testAggregateRepository(DomainEventContext domainEventContext) {
            return new TestAggregateRepository(domainEventContext);
        }

        @Bean
        InnerApplicationService innerApplicationService(TestAggregateRepository repository) {
            return new InnerApplicationService(repository);
        }

        @Bean
        OuterApplicationService outerApplicationService(TestAggregateRepository repository,
                                                        InnerApplicationService innerApplicationService) {
            return new OuterApplicationService(repository, innerApplicationService);
        }

        @Bean
        FailingApplicationService failingApplicationService(TestAggregateRepository repository) {
            return new FailingApplicationService(repository);
        }
    }

    @Autowired
    private RecordingDomainEventDispatcher dispatcher;

    @Autowired
    private OuterApplicationService outerApplicationService;

    @Autowired
    private FailingApplicationService failingApplicationService;

    @BeforeEach
    void setUp() {
        dispatcher.reset();
    }

    @Test
    void dispatchesRecordedEventsWhenApplicationServiceSucceeds() {
        outerApplicationService.handleSingle("order-1");

        assertThat(dispatcher.dispatchCallCount()).isEqualTo(1);
        assertThat(dispatcher.dispatchedEvents())
                .extracting(TestDomainEvent::aggregateId)
                .containsExactly("order-1");
    }

    @Test
    void nestedApplicationServicesReuseScopeAndDispatchOnceAtOuterBoundary() {
        outerApplicationService.handleNested("outer-1", "inner-1");

        assertThat(dispatcher.dispatchCallCount()).isEqualTo(1);
        assertThat(dispatcher.dispatchedEvents())
                .extracting(TestDomainEvent::aggregateId)
                .containsExactly("outer-1", "inner-1");
    }

    @Test
    void exceptionEscapingApplicationServiceDiscardsPendingEvents() {
        assertThatThrownBy(() -> failingApplicationService.handleAndFail("order-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(dispatcher.dispatchCallCount()).isZero();
        assertThat(dispatcher.dispatchedEvents()).isEmpty();
    }

    @ApplicationService
    static class OuterApplicationService {

        private final TestAggregateRepository repository;
        private final InnerApplicationService innerApplicationService;

        OuterApplicationService(TestAggregateRepository repository,
                                InnerApplicationService innerApplicationService) {
            this.repository = repository;
            this.innerApplicationService = innerApplicationService;
        }

        void handleSingle(String aggregateId) {
            repository.save(TestAggregate.create(aggregateId));
        }

        void handleNested(String outerAggregateId, String innerAggregateId) {
            repository.save(TestAggregate.create(outerAggregateId));
            innerApplicationService.handle(innerAggregateId);
        }
    }

    @ApplicationService
    static class InnerApplicationService {

        private final TestAggregateRepository repository;

        InnerApplicationService(TestAggregateRepository repository) {
            this.repository = repository;
        }

        void handle(String aggregateId) {
            repository.save(TestAggregate.create(aggregateId));
        }
    }

    @ApplicationService
    static class FailingApplicationService {

        private final TestAggregateRepository repository;

        FailingApplicationService(TestAggregateRepository repository) {
            this.repository = repository;
        }

        void handleAndFail(String aggregateId) {
            repository.save(TestAggregate.create(aggregateId));
            throw new IllegalStateException("boom");
        }
    }

    static final class TestAggregateRepository {

        private final DomainEventContext domainEventContext;

        TestAggregateRepository(DomainEventContext domainEventContext) {
            this.domainEventContext = domainEventContext;
        }

        void save(TestAggregate aggregate) {
            domainEventContext.register(aggregate);
        }
    }

    static final class RecordingDomainEventDispatcher implements DomainEventDispatcher {

        private final List<TestDomainEvent> dispatchedEvents = new ArrayList<>();
        private int dispatchCallCount;

        @Override
        public void dispatch(List<? extends DomainEvent> events) {
            dispatchCallCount++;
            for (DomainEvent event : events) {
                dispatchedEvents.add((TestDomainEvent) event);
            }
        }

        int dispatchCallCount() {
            return dispatchCallCount;
        }

        List<TestDomainEvent> dispatchedEvents() {
            return List.copyOf(dispatchedEvents);
        }

        void reset() {
            dispatchedEvents.clear();
            dispatchCallCount = 0;
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
