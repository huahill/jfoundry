package org.jfoundry.infrastructure.persistence.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/// Aggregate persistence state bound to the current Helidon JTA transaction.
@Dependent
public final class HelidonAggregatePersistenceContext implements AggregatePersistenceContext {

    private static final Object RESOURCE_KEY = new Object();

    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @Inject
    public HelidonAggregatePersistenceContext(TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionSynchronizationRegistry = Objects.requireNonNull(
                transactionSynchronizationRegistry, "TransactionSynchronizationRegistry must not be null");
    }

    @Override
    public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
        requireArguments(aggregate, key, state);
        Map<PersistenceStateKey<?>, Object> states = currentStore(true).computeIfAbsent(aggregate, ignored -> new HashMap<>());
        if (states.putIfAbsent(key, state) != null) {
            throw new IllegalStateException("Persistence state is already attached for aggregate and key: " + key);
        }
    }

    @Override
    public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
        Objects.requireNonNull(aggregate, "Aggregate must not be null");
        Objects.requireNonNull(key, "Persistence state key must not be null");
        Map<PersistenceStateKey<?>, Object> states = currentStore(false).get(aggregate);
        Object state = states == null ? null : states.get(key);
        if (state == null) throw new IllegalStateException("Aggregate persistence state is not tracked in the current transaction: " + key);
        return key.type().cast(state);
    }

    @Override
    public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
        requireArguments(aggregate, key, state);
        require(aggregate, key);
        currentStore(false).get(aggregate).put(key, state);
    }

    private Map<Object, Map<PersistenceStateKey<?>, Object>> currentStore(boolean create) {
        requireActiveTransaction();
        @SuppressWarnings("unchecked")
        Map<Object, Map<PersistenceStateKey<?>, Object>> store =
                (Map<Object, Map<PersistenceStateKey<?>, Object>>) transactionSynchronizationRegistry.getResource(RESOURCE_KEY);
        if (store == null && create) {
            store = new IdentityHashMap<>();
            transactionSynchronizationRegistry.putResource(RESOURCE_KEY, store);
        }
        if (store == null) throw new IllegalStateException("Aggregate persistence state is not tracked in the current transaction");
        return store;
    }

    private void requireActiveTransaction() {
        int status = transactionSynchronizationRegistry.getTransactionStatus();
        if (transactionSynchronizationRegistry.getTransactionKey() == null
                || (status != Status.STATUS_ACTIVE && status != Status.STATUS_MARKED_ROLLBACK)) {
            throw new IllegalStateException("Aggregate persistence state requires an active transaction.");
        }
    }

    private static <S> void requireArguments(Object aggregate, PersistenceStateKey<S> key, S state) {
        Objects.requireNonNull(aggregate, "Aggregate must not be null");
        Objects.requireNonNull(key, "Persistence state key must not be null");
        Objects.requireNonNull(state, "Persistence state must not be null");
    }
}
