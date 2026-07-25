package org.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jfoundry.application.transaction.TransactionRunner;

/// CDI test bean that proves a Helidon transaction commits to the configured database.
@ApplicationScoped
public class TransactionPersistenceVerifier {

    private final TransactionRunner transactionRunner;

    @PersistenceContext
    EntityManager entityManager;

    @Inject
    TransactionPersistenceVerifier(TransactionRunner transactionRunner) {
        this.transactionRunner = transactionRunner;
    }

    int persistAndCount() throws Exception {
        transactionRunner.run(() -> entityManager.persist(new TransactionVerificationRecord()));
        return transactionRunner.call(() -> entityManager.createQuery(
                        "select count(record) from TransactionVerificationRecord record", Long.class)
                .getSingleResult()
                .intValue());
    }
}
