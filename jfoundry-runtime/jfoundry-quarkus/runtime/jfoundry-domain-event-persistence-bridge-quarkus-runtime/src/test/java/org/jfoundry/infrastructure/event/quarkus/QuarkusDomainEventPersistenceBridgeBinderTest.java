package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserver;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserverAware;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarkusDomainEventPersistenceBridgeBinderTest {

    @Test
    void constructorRejectsNullDomainEventContext() {
        assertThatThrownBy(() -> new QuarkusDomainEventPersistenceBridgeBinder(null, instanceOf()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("DomainEventContext must not be null.");
    }

    @Test
    void constructorRejectsNullAwares() {
        assertThatThrownBy(() -> new QuarkusDomainEventPersistenceBridgeBinder(new RecordingContext(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AggregatePersistenceObserverAware instances must not be null.");
    }

    @Test
    void initializeBindsContextRegisterToAwares() {
        RecordingContext context = new RecordingContext();
        RecordingAware aware = new RecordingAware();

        new QuarkusDomainEventPersistenceBridgeBinder(context, instanceOf(aware)).initialize(null);

        TestAggregate aggregate = new TestAggregate(new TestAggregateId("one"));
        aware.observer.afterPersisted(aggregate);

        assertThat(context.registered).containsExactly(aggregate);
    }

    @SuppressWarnings("unchecked")
    private static Instance<AggregatePersistenceObserverAware> instanceOf(
            AggregatePersistenceObserverAware... awares) {
        List<AggregatePersistenceObserverAware> values = List.of(awares);
        return (Instance<AggregatePersistenceObserverAware>) Proxy.newProxyInstance(
                QuarkusDomainEventPersistenceBridgeBinderTest.class.getClassLoader(),
                new Class<?>[] {Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "forEach" -> {
                        values.forEach((Consumer<AggregatePersistenceObserverAware>) arguments[0]);
                        yield null;
                    }
                    case "iterator" -> values.iterator();
                    case "spliterator" -> values.spliterator();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingContext implements DomainEventContext {

        private final List<EventRecordable> registered = new ArrayList<>();

        @Override
        public void register(EventRecordable aggregate) {
            registered.add(aggregate);
        }
    }

    private static final class RecordingAware implements AggregatePersistenceObserverAware {

        private AggregatePersistenceObserver observer;

        @Override
        public void setAggregatePersistenceObserver(AggregatePersistenceObserver observer) {
            this.observer = observer;
        }
    }

    private static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }
    }

    private record TestAggregateId(String value) implements Identifier, Serializable {
    }
}
