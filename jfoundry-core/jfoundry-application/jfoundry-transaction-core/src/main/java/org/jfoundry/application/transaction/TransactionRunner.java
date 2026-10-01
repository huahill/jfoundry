package org.jfoundry.application.transaction;

/**
 * Runs application code inside an explicit transaction boundary.
 * Callbacks and these methods do not declare checked exceptions; runtime
 * exceptions propagate unchanged. Translate adapter-level checked failures
 * before they enter the callback.
 */
public interface TransactionRunner {

    default void run(TransactionAction action) {
        run(TransactionOptions.defaults(), action);
    }

    default <T> T call(TransactionCallback<T> callback) {
        return call(TransactionOptions.defaults(), callback);
    }

    default void run(TransactionOptions options, TransactionAction action) {
        call(options, () -> {
            action.execute();
            return null;
        });
    }

    <T> T call(TransactionOptions options, TransactionCallback<T> callback);
}
