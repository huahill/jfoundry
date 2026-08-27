package org.jfoundry.infrastructure.transaction.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class QuarkusTransactionRunnerCdiTest {

    @Inject
    TransactionRunner transactionRunner;

    @Test
    void exposesTransactionRunnerThroughCdi() {
        assertThat(transactionRunner).isInstanceOf(QuarkusTransactionRunner.class);
    }
}
