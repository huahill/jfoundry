package io.huahill.jfoundry.infrastructure.transaction.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import io.huahill.jfoundry.application.transaction.TransactionCallback;
import io.huahill.jfoundry.application.transaction.TransactionOptions;
import io.huahill.jfoundry.application.transaction.TransactionRunner;
import io.huahill.jfoundry.infrastructure.transaction.jta.JtaTransactionRunner;

/// Quarkus CDI registration for the shared Jakarta Transactions adapter.
@ApplicationScoped
public class QuarkusTransactionRunner implements TransactionRunner {

    private final JtaTransactionRunner delegate;

    @Inject
    public QuarkusTransactionRunner(TransactionManager transactionManager) {
        this.delegate = new JtaTransactionRunner(transactionManager);
    }

    @Override
    public <T> T call(TransactionOptions options, TransactionCallback<T> callback) {
        return this.delegate.call(options, callback);
    }
}
