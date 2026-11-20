package io.huahill.jfoundry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregatePersistenceContextContractTest {

    private static final PersistenceStateKey<Long> VERSION =
            PersistenceStateKey.of("version", Long.class);

    private final AggregatePersistenceContext context = new TestContext();

    @Test
    void attachesAndReplacesTypedState() {
        Object aggregate = new Object();

        context.attach(aggregate, VERSION, 3L);
        assertThat(context.require(aggregate, VERSION)).isEqualTo(3L);

        context.replace(aggregate, VERSION, 4L);
        assertThat(context.require(aggregate, VERSION)).isEqualTo(4L);
    }

    @Test
    void tracksAggregateInstancesByIdentity() {
        EqualAggregate first = new EqualAggregate("same-id");
        EqualAggregate second = new EqualAggregate("same-id");

        context.attach(first, VERSION, 1L);

        assertThatThrownBy(() -> context.require(second, VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");
    }

    @Test
    void rejectsDuplicateAttachment() {
        Object aggregate = new Object();
        context.attach(aggregate, VERSION, 1L);

        assertThatThrownBy(() -> context.attach(aggregate, VERSION, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already attached");
    }

    @Test
    void rejectsMissingStateForRequireAndReplace() {
        Object aggregate = new Object();

        assertThatThrownBy(() -> context.require(aggregate, VERSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");
        assertThatThrownBy(() -> context.replace(aggregate, VERSION, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");
    }

    @Test
    void rejectsNullContractArguments() {
        Object aggregate = new Object();

        assertThatThrownBy(() -> PersistenceStateKey.of(null, Long.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PersistenceStateKey.of("version", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.attach(null, VERSION, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.attach(aggregate, null, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.attach(aggregate, VERSION, null))
                .isInstanceOf(NullPointerException.class);
    }

    private record EqualAggregate(String id) {
    }

    private static final class TestContext implements AggregatePersistenceContext {

        private final Map<Object, Map<PersistenceStateKey<?>, Object>> states =
                new IdentityHashMap<>();

        @Override
        public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
            requireArguments(aggregate, key, state);
            Map<PersistenceStateKey<?>, Object> aggregateStates =
                    states.computeIfAbsent(aggregate, ignored -> new HashMap<>());
            if (aggregateStates.putIfAbsent(key, state) != null) {
                throw new IllegalStateException("Persistence state is already attached: " + key.name());
            }
        }

        @Override
        public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
            if (aggregate == null || key == null) {
                throw new NullPointerException("Aggregate and state key must not be null.");
            }
            Map<PersistenceStateKey<?>, Object> aggregateStates = states.get(aggregate);
            Object state = aggregateStates == null ? null : aggregateStates.get(key);
            if (state == null) {
                throw new IllegalStateException("Aggregate persistence state is not tracked: " + key.name());
            }
            return key.type().cast(state);
        }

        @Override
        public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
            requireArguments(aggregate, key, state);
            require(aggregate, key);
            states.get(aggregate).put(key, state);
        }

        private static <S> void requireArguments(
                Object aggregate, PersistenceStateKey<S> key, S state) {
            if (aggregate == null || key == null || state == null) {
                throw new NullPointerException("Aggregate, state key, and state must not be null.");
            }
        }
    }
}
