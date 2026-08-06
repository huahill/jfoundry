package org.jfoundry.infrastructure.transaction.helidon;

import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelidonTransactionRunnerTest {

    @Test
    void startsAndCommitsAnApplicationTransaction() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.defaults(), () -> "confirmed");

        assertThat(result).isEqualTo("confirmed");
        assertThat(transactionManager.begins).isEqualTo(1);
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void joinsAnExistingTransactionAndMarksItRollbackOnlyWhenTheCallbackFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.active = true;
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(() -> {
            throw new IOException("write failed");
        })).isInstanceOf(IOException.class);

        assertThat(transactionManager.begins).isZero();
        assertThat(transactionManager.rollbackOnlyMarks).isEqualTo(1);
    }

    @Test
    void suspendsAndResumesAnExistingTransactionForRequiresNew() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.active = true;
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        runner.call(TransactionOptions.builder().propagation(TransactionPropagation.REQUIRES_NEW).build(), () -> "confirmed");

        assertThat(transactionManager.suspends).isEqualTo(1);
        assertThat(transactionManager.resumes).isEqualTo(1);
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void runsSupportsWithoutOpeningATransaction() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        runner.call(TransactionOptions.builder().propagation(TransactionPropagation.SUPPORTS).build(), () -> "confirmed");

        assertThat(transactionManager.begins).isZero();
    }

    @Test
    void rejectsMandatoryWithoutAnExistingTransaction() {
        HelidonTransactionRunner runner = new HelidonTransactionRunner(new RecordingTransactionManager());

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.MANDATORY).build(), () -> "confirmed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANDATORY");
    }

    @Test
    void suspendsAnExistingTransactionForNotSupported() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.active = true;
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        runner.call(TransactionOptions.builder().propagation(TransactionPropagation.NOT_SUPPORTED).build(), () -> "confirmed");

        assertThat(transactionManager.suspends).isEqualTo(1);
        assertThat(transactionManager.resumes).isEqualTo(1);
    }

    @Test
    void rejectsNeverWhenATransactionExists() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.active = true;
        HelidonTransactionRunner runner = new HelidonTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.NEVER).build(), () -> "confirmed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEVER");
    }

    private static final class RecordingTransactionManager implements TransactionManager {

        private int begins;
        private int commits;
        private int rollbackOnlyMarks;
        private int suspends;
        private int resumes;
        private boolean active;
        private Transaction suspended;

        @Override
        public void begin() {
            active = true;
            begins++;
        }

        @Override
        public void commit() {
            active = false;
            commits++;
        }

        @Override
        public int getStatus() {
            return active ? Status.STATUS_ACTIVE : Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public Transaction getTransaction() {
            return null;
        }

        @Override
        public void resume(Transaction transaction) {
            active = transaction != null;
            resumes++;
        }

        @Override
        public Transaction suspend() {
            suspended = new Transaction() {
                @Override public void commit() { }
                @Override public boolean delistResource(javax.transaction.xa.XAResource resource, int flag) { return false; }
                @Override public boolean enlistResource(javax.transaction.xa.XAResource resource) { return false; }
                @Override public int getStatus() { return Status.STATUS_ACTIVE; }
                @Override public void registerSynchronization(jakarta.transaction.Synchronization synchronization) { }
                @Override public void rollback() { }
                @Override public void setRollbackOnly() { }
            };
            active = false;
            suspends++;
            return suspended;
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
            rollbackOnlyMarks++;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
        }
    }
}
