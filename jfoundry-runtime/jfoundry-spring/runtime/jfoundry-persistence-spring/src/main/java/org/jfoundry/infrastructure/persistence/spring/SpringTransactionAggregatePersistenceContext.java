package org.jfoundry.infrastructure.persistence.spring;

import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/// Spring transaction-bound aggregate persistence state.
/// <p>
/// Business code does not manage this context's lifecycle. State is created lazily in an active
/// transaction and is suspended, resumed, and cleared with that transaction.
public final class SpringTransactionAggregatePersistenceContext
        implements AggregatePersistenceContext {

    @Override
    public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
        requireArguments(aggregate, key, state);
        StateStore store = currentStore(true);
        Map<PersistenceStateKey<?>, Object> aggregateStates =
                store.states.computeIfAbsent(aggregate, ignored -> new HashMap<>());
        if (aggregateStates.putIfAbsent(key, state) != null) {
            throw new IllegalStateException(
                    "Persistence state is already attached for aggregate and key: " + key);
        }
    }

    @Override
    public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
        Objects.requireNonNull(aggregate, "Aggregate must not be null.");
        Objects.requireNonNull(key, "Persistence state key must not be null.");
        StateStore store = currentStore(false);
        Map<PersistenceStateKey<?>, Object> aggregateStates =
                store == null ? null : store.states.get(aggregate);
        Object state = aggregateStates == null ? null : aggregateStates.get(key);
        if (state == null) {
            throw new IllegalStateException(
                    "Aggregate persistence state is not tracked in the current transaction: " + key);
        }
        return key.type().cast(state);
    }

    @Override
    public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
        requireArguments(aggregate, key, state);
        require(aggregate, key);
        currentStore(false).states.get(aggregate).put(key, state);
    }

    private StateStore currentStore(boolean create) {
        requireActiveTransaction();
        StateStore existing = (StateStore) TransactionSynchronizationManager.getResource(this);
        if (existing != null || !create) {
            return existing;
        }

        StateStore created = new StateStore();
        TransactionSynchronizationManager.bindResource(this, created);
        TransactionSynchronizationManager.registerSynchronization(
                new StoreSynchronization(this, created));
        return created;
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Aggregate persistence state requires an active transaction.");
        }
    }

    private static <S> void requireArguments(
            Object aggregate, PersistenceStateKey<S> key, S state) {
        Objects.requireNonNull(aggregate, "Aggregate must not be null.");
        Objects.requireNonNull(key, "Persistence state key must not be null.");
        Objects.requireNonNull(state, "Persistence state must not be null.");
    }

    private static final class StateStore {
        private final Map<Object, Map<PersistenceStateKey<?>, Object>> states =
                new IdentityHashMap<>();
    }

    private static final class StoreSynchronization implements TransactionSynchronization {

        private final SpringTransactionAggregatePersistenceContext resourceKey;
        private final StateStore store;

        private StoreSynchronization(
                SpringTransactionAggregatePersistenceContext resourceKey,
                StateStore store) {
            this.resourceKey = resourceKey;
            this.store = store;
        }

        @Override
        public void suspend() {
            unbindIfCurrent();
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(resourceKey, store);
        }

        @Override
        public void afterCompletion(int status) {
            unbindIfCurrent();
            store.states.clear();
        }

        private void unbindIfCurrent() {
            if (TransactionSynchronizationManager.hasResource(resourceKey)
                    && TransactionSynchronizationManager.getResource(resourceKey) == store) {
                TransactionSynchronizationManager.unbindResource(resourceKey);
            }
        }
    }
}
