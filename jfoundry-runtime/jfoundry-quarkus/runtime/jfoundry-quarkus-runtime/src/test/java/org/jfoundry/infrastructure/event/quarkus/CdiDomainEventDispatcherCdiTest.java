package org.jfoundry.infrastructure.event.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class CdiDomainEventDispatcherCdiTest {

    @Inject
    EventApplicationService applicationService;

    @Inject
    TransactionRunner transactionRunner;

    @Inject
    RecordingObserver observer;

    @BeforeEach
    void resetObserver() {
        observer.reset();
    }

    @Test
    void publishesImmediatelyWhenNoTransactionIsActive() {
        applicationService.handle("without-transaction");

        assertThat(observer.events()).extracting(TestDomainEvent::aggregateId)
                .containsExactly("without-transaction");
    }

    @Test
    void publishesOnlyAfterTheTransactionCommits() throws Exception {
        transactionRunner.run(() -> {
            applicationService.handle("committed");
            assertThat(observer.events()).isEmpty();
        });

        assertThat(observer.events()).extracting(TestDomainEvent::aggregateId)
                .containsExactly("committed");
    }

    @Test
    void doesNotPublishWhenTheTransactionRollsBack() {
        assertThatThrownBy(() -> transactionRunner.run(() -> {
            applicationService.handle("rolled-back");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback");

        assertThat(observer.events()).isEmpty();
    }

    @ApplicationScoped
    @QuarkusDomainEventDispatch
    static class EventApplicationService {

        @Inject
        DomainEventContext domainEventContext;

        void handle(String aggregateId) {
            domainEventContext.register(TestAggregate.create(aggregateId));
        }
    }

    @ApplicationScoped
    static class RecordingObserver {

        private final List<TestDomainEvent> events = new ArrayList<>();

        void observe(@Observes TestDomainEvent event) {
            events.add(event);
        }

        List<TestDomainEvent> events() {
            return List.copyOf(events);
        }

        void reset() {
            events.clear();
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
