package org.jfoundry.infrastructure.persistence.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/// Aggregate persistence state bound to the current Quarkus JTA transaction.
/// <p>
/// JTA owns the resource lifecycle, including transaction completion, suspension, and resumption.
/// Business code must access this context only inside an active transaction.
@DefaultBean
@ApplicationScoped
public final class QuarkusAggregatePersistenceContext
        implements AggregatePersistenceContext {

    private static final Object RESOURCE_KEY = new Object();

    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public QuarkusAggregatePersistenceContext(
            TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionSynchronizationRegistry = Objects.requireNonNull(
                transactionSynchronizationRegistry,
                "TransactionSynchronizationRegistry must not be null.");
    }

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
        StateStore existing = (StateStore) transactionSynchronizationRegistry.getResource(RESOURCE_KEY);
        if (existing != null || !create) {
            return existing;
        }

        StateStore created = new StateStore();
        transactionSynchronizationRegistry.putResource(RESOURCE_KEY, created);
        return created;
    }

    private void requireActiveTransaction() {
        int status = transactionSynchronizationRegistry.getTransactionStatus();
        if (transactionSynchronizationRegistry.getTransactionKey() == null
                || (status != Status.STATUS_ACTIVE && status != Status.STATUS_MARKED_ROLLBACK)) {
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
}
