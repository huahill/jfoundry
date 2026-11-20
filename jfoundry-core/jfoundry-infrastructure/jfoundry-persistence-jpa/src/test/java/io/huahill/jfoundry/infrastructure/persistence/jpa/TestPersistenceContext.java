package io.huahill.jfoundry.infrastructure.persistence.jpa;

import io.huahill.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import io.huahill.jfoundry.infrastructure.persistence.PersistenceStateKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

final class TestPersistenceContext implements AggregatePersistenceContext {

    private final Map<Object, Map<PersistenceStateKey<?>, Object>> states =
            new IdentityHashMap<>();

    @Override
    public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
        Map<PersistenceStateKey<?>, Object> values =
                states.computeIfAbsent(aggregate, ignored -> new HashMap<>());
        if (values.putIfAbsent(key, state) != null) {
            throw new IllegalStateException("already attached");
        }
    }

    @Override
    public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
        Object state = states.getOrDefault(aggregate, Map.of()).get(key);
        if (state == null) {
            throw new IllegalStateException("Aggregate persistence state is not tracked");
        }
        return key.type().cast(state);
    }

    @Override
    public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
        require(aggregate, key);
        states.get(aggregate).put(key, state);
    }
}
