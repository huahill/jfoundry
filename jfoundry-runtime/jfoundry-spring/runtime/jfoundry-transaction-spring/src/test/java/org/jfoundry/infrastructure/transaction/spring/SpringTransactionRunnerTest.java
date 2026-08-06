package org.jfoundry.infrastructure.transaction.spring;

import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringTransactionRunnerTest {

    @Test
    void mapsTransactionOptionsToSpringTransactionTemplate() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        SpringTransactionRunner runner = new SpringTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.builder()
                .name("install-app")
                .readOnly(true)
                .timeout(Duration.ofSeconds(7))
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build(), () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(transactionManager.definition.getName()).isEqualTo("install-app");
        assertThat(transactionManager.definition.isReadOnly()).isTrue();
        assertThat(transactionManager.definition.getTimeout()).isEqualTo(7);
        assertThat(transactionManager.definition.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isZero();
    }

    @Test
    void rollsBackAndRethrowsOriginalCheckedException() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        SpringTransactionRunner runner = new SpringTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(() -> {
            throw new IOException("write failed");
        }))
                .isInstanceOf(IOException.class)
                .hasMessage("write failed");

        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {

        private TransactionDefinition definition;
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            this.definition = definition;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
