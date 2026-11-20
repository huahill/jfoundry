package io.huahill.jfoundry.infrastructure.transaction.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import io.huahill.jfoundry.application.transaction.TransactionCallback;
import io.huahill.jfoundry.application.transaction.TransactionOptions;
import io.huahill.jfoundry.application.transaction.TransactionRunner;
import io.huahill.jfoundry.infrastructure.transaction.jta.JtaTransactionRunner;

/// Helidon MP CDI registration for the shared Jakarta Transactions adapter.
@Dependent
public class HelidonTransactionRunner implements TransactionRunner {

    private final JtaTransactionRunner delegate;

    @Inject
    public HelidonTransactionRunner(TransactionManager transactionManager) {
        this.delegate = new JtaTransactionRunner(transactionManager);
    }

    @Override
    public <T> T call(TransactionOptions options, TransactionCallback<T> callback) {
        return this.delegate.call(options, callback);
    }
}
