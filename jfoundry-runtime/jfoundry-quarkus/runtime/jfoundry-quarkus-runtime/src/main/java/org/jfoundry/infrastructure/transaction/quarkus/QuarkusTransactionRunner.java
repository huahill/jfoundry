package org.jfoundry.infrastructure.transaction.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;

import java.util.Objects;

/// Quarkus CDI adapter for explicit application transaction boundaries.
@ApplicationScoped
public class QuarkusTransactionRunner implements TransactionRunner {

    private final TransactionManager transactionManager;

    @Inject
    public QuarkusTransactionRunner(TransactionManager transactionManager) {
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
        } catch (Exception ex) {
            if (transactionStarted) {
                rollbackIfActive();
            }
            throw ex;
        } catch (Error error) {
            if (transactionStarted) {
                rollbackIfActive();
            }
            throw error;
        } finally {
            if (timeoutConfigured) {
                transactionManager.setTransactionTimeout(0);
            }
        }
    }

    private <T> T callInExistingTransaction(TransactionCallback<T> callback) throws Exception {
        try {
            return callback.execute();
        } catch (Exception ex) {
            transactionManager.setRollbackOnly();
            throw ex;
        } catch (Error error) {
            transactionManager.setRollbackOnly();
            throw error;
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
        try {
            return callback.execute();
        } finally {
            transactionManager.resume(suspended);
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
}
