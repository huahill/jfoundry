package org.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxDomainEventDispatcherTest {

    @Test
    void declaresThatItMustRunBeforeTransactionCommit() {
        assertThat(BeforeCommitDomainEventDispatcher.class)
                .isAssignableFrom(HelidonOutboxDomainEventDispatcher.class);
    }

    @Test
    void doesNotResolveTheRecorderWhenAutomaticExternalizationIsDisabled() {
        new HelidonOutboxDomainEventDispatcher(unavailableRecorder(), false).dispatch(List.of(new Event()));
    }

    @Test
    void recordsTheDomainEventBatchWhenAutomaticExternalizationIsEnabled() {
        RecordingRecorder recorder = new RecordingRecorder();

        new HelidonOutboxDomainEventDispatcher(resolvableRecorder(recorder), true).dispatch(List.of(new Event()));

        assertThat(recorder.events).containsExactly(new Event());
    }

    @SuppressWarnings("unchecked")
    private static Instance<DomainEventOutboxRecorder> unavailableRecorder() {
        return (Instance<DomainEventOutboxRecorder>) Proxy.newProxyInstance(
                HelidonOutboxDomainEventDispatcherTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Disabled externalization must not resolve the recorder");
                });
    }

    @SuppressWarnings("unchecked")
    private static Instance<DomainEventOutboxRecorder> resolvableRecorder(DomainEventOutboxRecorder recorder) {
        return (Instance<DomainEventOutboxRecorder>) Proxy.newProxyInstance(
                HelidonOutboxDomainEventDispatcherTest.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isResolvable" -> true;
                    case "get" -> recorder;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingRecorder implements DomainEventOutboxRecorder {
        private List<DomainEvent> events = List.of();

        @Override
        public void record(List<? extends DomainEvent> events) {
            this.events = events.stream().map(DomainEvent.class::cast).toList();
        }
    }

    private record Event() implements DomainEvent {
    }
}
