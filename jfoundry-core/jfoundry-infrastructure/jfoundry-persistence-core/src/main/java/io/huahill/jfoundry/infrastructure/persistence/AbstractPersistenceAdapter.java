package io.huahill.jfoundry.infrastructure.persistence;

import java.util.Objects;
import java.util.function.Supplier;

/// Storage-neutral support for persistence adapters that translate technical failures.
/// <p>
/// Aggregate repositories inherit this support but retain their own lifecycle and domain-event
/// behavior. Query readers and projection stores use the protected operation methods around their
/// responsibility-specific persistence calls.
public abstract class AbstractPersistenceAdapter {

    private PersistenceFailureTranslator persistenceFailureTranslator =
            PersistenceFailureTranslator.passThrough();

    /// Injects an optional runtime-specific persistence failure translator.
    public final void setPersistenceFailureTranslator(
            PersistenceFailureTranslator persistenceFailureTranslator) {
        this.persistenceFailureTranslator = Objects.requireNonNull(
                persistenceFailureTranslator, "PersistenceFailureTranslator must not be null.");
    }

    protected final <R> R find(Supplier<R> persistenceCall) {
        return execute(PersistenceOperation.FIND, persistenceCall);
    }

    protected final <R> R query(Supplier<R> persistenceCall) {
        return execute(PersistenceOperation.QUERY, persistenceCall);
    }

    protected final void add(Runnable persistenceCall) {
        execute(PersistenceOperation.ADD, persistenceCall);
    }

    protected final <R> R add(Supplier<R> persistenceCall) {
        return execute(PersistenceOperation.ADD, persistenceCall);
    }

    protected final void modify(Runnable persistenceCall) {
        execute(PersistenceOperation.MODIFY, persistenceCall);
    }

    protected final <R> R modify(Supplier<R> persistenceCall) {
        return execute(PersistenceOperation.MODIFY, persistenceCall);
    }

    protected final void remove(Runnable persistenceCall) {
        execute(PersistenceOperation.REMOVE, persistenceCall);
    }

    protected final <R> R remove(Supplier<R> persistenceCall) {
        return execute(PersistenceOperation.REMOVE, persistenceCall);
    }

    private void execute(PersistenceOperation operation, Runnable persistenceCall) {
        Objects.requireNonNull(persistenceCall, "Persistence call must not be null.");
        execute(operation, () -> {
            persistenceCall.run();
            return null;
        });
    }

    private <R> R execute(PersistenceOperation operation, Supplier<R> persistenceCall) {
        Objects.requireNonNull(operation, "Persistence operation must not be null.");
        Objects.requireNonNull(persistenceCall, "Persistence call must not be null.");
        try {
            return persistenceCall.get();
        } catch (RuntimeException failure) {
            RuntimeException translated = persistenceFailureTranslator.translate(operation, failure);
            if (translated == null) {
                throw new IllegalStateException(
                        "PersistenceFailureTranslator must not return null.", failure);
            }
            throw translated;
        }
    }
}
