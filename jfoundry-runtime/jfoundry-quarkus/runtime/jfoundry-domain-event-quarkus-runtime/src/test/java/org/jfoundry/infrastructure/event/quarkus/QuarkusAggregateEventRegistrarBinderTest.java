package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.persistence.AggregateEventRegistrar;
import org.jfoundry.infrastructure.persistence.AggregateEventRegistrarAware;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarkusAggregateEventRegistrarBinderTest {

    @Test
    void constructorRejectsNullDomainEventContext() {
        assertThatThrownBy(() -> new QuarkusAggregateEventRegistrarBinder(null, instanceOf()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("DomainEventContext must not be null.");
    }

    @Test
    void constructorRejectsNullAwares() {
        assertThatThrownBy(() -> new QuarkusAggregateEventRegistrarBinder(new RecordingContext(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AggregateEventRegistrarAware instances must not be null.");
    }

    @Test
    void initializeBindsContextRegisterToAwares() {
        RecordingContext context = new RecordingContext();
        RecordingAware aware = new RecordingAware();

        new QuarkusAggregateEventRegistrarBinder(context, instanceOf(aware)).initialize(null);

        EventRecordable aggregate = () -> List.of();
        aware.registrar.register(aggregate);

        assertThat(context.registered).containsExactly(aggregate);
    }

    @SuppressWarnings("unchecked")
    private static Instance<AggregateEventRegistrarAware> instanceOf(AggregateEventRegistrarAware... awares) {
        List<AggregateEventRegistrarAware> values = List.of(awares);
        return (Instance<AggregateEventRegistrarAware>) Proxy.newProxyInstance(
                QuarkusAggregateEventRegistrarBinderTest.class.getClassLoader(),
                new Class<?>[] {Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "forEach" -> {
                        values.forEach((Consumer<AggregateEventRegistrarAware>) arguments[0]);
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

    private static final class RecordingAware implements AggregateEventRegistrarAware {

        private AggregateEventRegistrar registrar;

        @Override
        public void setAggregateEventRegistrar(AggregateEventRegistrar registrar) {
            this.registrar = registrar;
        }
    }
}
