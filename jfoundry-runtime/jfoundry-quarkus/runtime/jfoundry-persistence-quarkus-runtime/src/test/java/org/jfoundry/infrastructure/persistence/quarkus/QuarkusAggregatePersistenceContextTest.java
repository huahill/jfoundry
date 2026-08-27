package org.jfoundry.infrastructure.persistence.quarkus;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarkusAggregatePersistenceContextTest {

    private static final PersistenceStateKey<String> MANAGED_ENTITY =
            PersistenceStateKey.of("managed-entity", String.class);

    private final RecordingTransactionSynchronizationRegistry transactionRegistry =
            new RecordingTransactionSynchronizationRegistry();
    private final AggregatePersistenceContext context =
            new QuarkusAggregatePersistenceContext(transactionRegistry);

    @Test
    void tracksAggregateStateByIdentityWithinTheActiveTransaction() {
        transactionRegistry.activate("first");
        EqualAggregate first = new EqualAggregate("order-1");
        EqualAggregate second = new EqualAggregate("order-1");

        context.attach(first, MANAGED_ENTITY, "managed-first");

        assertThat(context.require(first, MANAGED_ENTITY)).isEqualTo("managed-first");
        assertThatThrownBy(() -> context.require(second, MANAGED_ENTITY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");
    }

    @Test
    void restoresTheOuterTransactionStateAfterAnInnerTransactionCompletes() {
        transactionRegistry.activate("outer");
        Object outerAggregate = new Object();
        context.attach(outerAggregate, MANAGED_ENTITY, "outer-managed");

        transactionRegistry.activate("inner");
        Object innerAggregate = new Object();
        context.attach(innerAggregate, MANAGED_ENTITY, "inner-managed");
        assertThat(context.require(innerAggregate, MANAGED_ENTITY)).isEqualTo("inner-managed");

        transactionRegistry.activate("outer");
        assertThat(context.require(outerAggregate, MANAGED_ENTITY)).isEqualTo("outer-managed");
        assertThatThrownBy(() -> context.require(innerAggregate, MANAGED_ENTITY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");
    }

    @Test
    void rejectsPersistenceStateOutsideAnActiveTransaction() {
        assertThatThrownBy(() -> context.attach(new Object(), MANAGED_ENTITY, "managed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    private record EqualAggregate(String id) {
    }

    private static final class RecordingTransactionSynchronizationRegistry
            implements TransactionSynchronizationRegistry {

        private final Map<Object, Map<Object, Object>> resources = new HashMap<>();
        private Object transactionKey;

        private void activate(Object transactionKey) {
            this.transactionKey = transactionKey;
        }

        @Override
        public Object getTransactionKey() {
            return transactionKey;
        }

        @Override
        public void putResource(Object key, Object value) {
            resources.computeIfAbsent(transactionKey, ignored -> new IdentityHashMap<>()).put(key, value);
        }

        @Override
        public Object getResource(Object key) {
            Map<Object, Object> transactionResources = resources.get(transactionKey);
            return transactionResources == null ? null : transactionResources.get(key);
        }

        @Override
        public int getTransactionStatus() {
            return transactionKey == null ? Status.STATUS_NO_TRANSACTION : Status.STATUS_ACTIVE;
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRollbackOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getRollbackOnly() {
            throw new UnsupportedOperationException();
        }

    }
}
