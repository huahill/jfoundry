package org.jfoundry.infrastructure.transaction.jta;

import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;

import java.util.Objects;

/// Jakarta Transactions adapter for explicit application transaction boundaries.
public class JtaTransactionRunner implements TransactionRunner {

    private final TransactionManager transactionManager;

    /// Creates an adapter backed by the runtime's Jakarta transaction manager.
    public JtaTransactionRunner(TransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    }

    @Override
    public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        if (options.readOnly()) {
            throw new UnsupportedOperationException("Jakarta Transactions does not support read-only transactions");
        }
        if (options.name().isPresent()) {
            throw new UnsupportedOperationException("Jakarta Transactions does not support transaction names");
        }

        return switch (options.propagation()) {
            case REQUIRED -> isTransactionActive()
                    ? callInExistingTransaction(callback)
                    : callInNewTransaction(options, callback);
            case REQUIRES_NEW -> callInNewTransactionSuspendingExisting(options, callback);
            case SUPPORTS -> isTransactionActive()
                    ? callInExistingTransaction(callback)
                    : callback.execute();
            case MANDATORY -> callInMandatoryTransaction(callback);
            case NOT_SUPPORTED -> callSuspendingExisting(callback);
            case NEVER -> callWithoutTransaction(callback);
        };
    }

    private <T> T callInNewTransactionSuspendingExisting(
            TransactionOptions options, TransactionCallback<T> callback) throws Exception {
        return callSuspendingExisting(() -> callInNewTransaction(options, callback));
    }

    private <T> T callInNewTransaction(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
        boolean timeoutConfigured = false;
        boolean transactionStarted = false;
        Throwable failure = null;
        try {
            if (options.timeout().isPresent()) {
                transactionManager.setTransactionTimeout(Math.toIntExact(options.timeout().get().toSeconds()));
                timeoutConfigured = true;
            }
            transactionManager.begin();
            transactionStarted = true;
            T result = callback.execute();
            transactionManager.commit();
            return result;
        } catch (Exception | Error ex) {
            failure = ex;
            if (transactionStarted) {
                runCleanupPreserving(ex, this::rollbackIfActive);
            }
            throw ex;
        } finally {
            if (timeoutConfigured) {
                if (failure == null) {
                    transactionManager.setTransactionTimeout(0);
                } else {
                    runCleanupPreserving(failure, () -> transactionManager.setTransactionTimeout(0));
                }
            }
        }
    }

    private <T> T callInExistingTransaction(TransactionCallback<T> callback) throws Exception {
        try {
            return callback.execute();
        } catch (Exception | Error ex) {
            runCleanupPreserving(ex, transactionManager::setRollbackOnly);
            throw ex;
        }
    }

    private <T> T callInMandatoryTransaction(TransactionCallback<T> callback) throws Exception {
        if (!isTransactionActive()) {
            throw new IllegalStateException("Transaction propagation MANDATORY requires an active transaction");
        }
        return callInExistingTransaction(callback);
    }

    private <T> T callSuspendingExisting(TransactionCallback<T> callback) throws Exception {
        if (!isTransactionActive()) {
            return callback.execute();
        }

        Transaction suspended = transactionManager.suspend();
        Throwable failure = null;
        try {
            return callback.execute();
        } catch (Exception | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            if (failure == null) {
                transactionManager.resume(suspended);
            } else {
                runCleanupPreserving(failure, () -> transactionManager.resume(suspended));
            }
        }
    }

    private <T> T callWithoutTransaction(TransactionCallback<T> callback) throws Exception {
        if (isTransactionActive()) {
            throw new IllegalStateException("Transaction propagation NEVER does not allow an active transaction");
        }
        return callback.execute();
    }

    private boolean isTransactionActive() throws Exception {
        int status = transactionManager.getStatus();
        return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
    }

    private void rollbackIfActive() throws Exception {
        if (isTransactionActive()) {
            transactionManager.rollback();
        }
    }

    private static void runCleanupPreserving(Throwable failure, CleanupOperation cleanup) {
        try {
            cleanup.run();
        } catch (Exception | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    @FunctionalInterface
    private interface CleanupOperation {
        void run() throws Exception;
    }
}
