package org.jfoundry.infrastructure.transaction.jta;

import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class JtaTransactionRunnerTest {

    @Test
    void startsAndCommitsTransactionWhenNoTransactionExists() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.builder()
                .timeout(Duration.ofSeconds(12))
                .build(), () -> "confirmed");

        assertThat(result).isEqualTo("confirmed");
        assertThat(transactionManager.begins).isEqualTo(1);
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isZero();
        assertThat(transactionManager.timeoutSeconds).containsExactly(12, 0);
    }

    @Test
    void joinsAnExistingTransactionAndMarksItRollbackOnlyWhenTheCallbackFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(() -> {
            throw new IOException("write failed");
        }))
                .isInstanceOf(IOException.class)
                .hasMessage("write failed");

        assertThat(transactionManager.begins).isZero();
        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isZero();
        assertThat(transactionManager.rollbackOnlyMarks).isEqualTo(1);
    }

    @Test
    void suspendsAnExistingTransactionForRequiresNewAndResumesItAfterCommit() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build(), () -> "confirmed");

        assertThat(result).isEqualTo("confirmed");
        assertThat(transactionManager.suspends).isEqualTo(1);
        assertThat(transactionManager.resumes).isEqualTo(1);
        assertThat(transactionManager.begins).isEqualTo(1);
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.isExistingTransactionActive()).isTrue();
    }

    @Test
    void runsWithoutATransactionForNotSupportedAndResumesTheExistingTransaction() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.NOT_SUPPORTED)
                .build(), () -> {
            assertThat(transactionManager.active).isFalse();
            return "confirmed";
        });

        assertThat(result).isEqualTo("confirmed");
        assertThat(transactionManager.suspends).isEqualTo(1);
        assertThat(transactionManager.resumes).isEqualTo(1);
        assertThat(transactionManager.begins).isZero();
        assertThat(transactionManager.isExistingTransactionActive()).isTrue();
    }

    @Test
    void runsWithoutStartingATransactionForSupportsWhenNoneExists() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        String result = runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.SUPPORTS)
                .build(), () -> "confirmed");

        assertThat(result).isEqualTo("confirmed");
        assertThat(transactionManager.begins).isZero();
        assertThat(transactionManager.commits).isZero();
    }

    @Test
    void rollsBackAnOwnedTransactionAndRethrowsTheOriginalCheckedException() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(() -> {
            throw new IOException("write failed");
        }))
                .isInstanceOf(IOException.class)
                .hasMessage("write failed");

        assertThat(transactionManager.begins).isEqualTo(1);
        assertThat(transactionManager.commits).isZero();
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void rollsBackAnOwnedTransactionAndRethrowsTheOriginalError() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);
        AssertionError failure = new AssertionError("fatal failure");

        Throwable thrown = catchThrowable(() -> runner.call(() -> {
            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void preservesTheOriginalFailureWhenRollbackAndTimeoutResetAlsoFail() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        RuntimeException rollbackFailure = new IllegalStateException("rollback failed");
        RuntimeException timeoutResetFailure = new IllegalStateException("timeout reset failed");
        transactionManager.rollbackFailure = rollbackFailure;
        transactionManager.timeoutResetFailure = timeoutResetFailure;
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);
        IOException failure = new IOException("write failed");

        Throwable thrown = catchThrowable(() -> runner.call(TransactionOptions.builder()
                .timeout(Duration.ofSeconds(12))
                .build(), () -> {
            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(thrown.getSuppressed()).containsExactly(rollbackFailure, timeoutResetFailure);
    }

    @Test
    void preservesTheOriginalFailureWhenMarkingAnExistingTransactionRollbackOnlyFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        RuntimeException cleanupFailure = new IllegalStateException("rollback-only failed");
        transactionManager.rollbackOnlyFailure = cleanupFailure;
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);
        IOException failure = new IOException("write failed");

        Throwable thrown = catchThrowable(() -> runner.call(() -> {
            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(thrown.getSuppressed()).containsExactly(cleanupFailure);
    }

    @Test
    void preservesTheOriginalFailureWhenResumingASuspendedTransactionFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        RuntimeException cleanupFailure = new IllegalStateException("resume failed");
        transactionManager.resumeFailure = cleanupFailure;
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);
        IOException failure = new IOException("write failed");

        Throwable thrown = catchThrowable(() -> runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.NOT_SUPPORTED)
                .build(), () -> {
            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(thrown.getSuppressed()).containsExactly(cleanupFailure);
    }

    @Test
    void requiresAnExistingTransactionForMandatory() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.MANDATORY)
                .build(), () -> "confirmed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANDATORY");
    }

    @Test
    void rejectsNeverWhenATransactionExists() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.activateExistingTransaction();
        JtaTransactionRunner runner = new JtaTransactionRunner(transactionManager);

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .propagation(TransactionPropagation.NEVER)
                .build(), () -> "confirmed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEVER");
    }

    @Test
    void rejectsReadOnlyTransactionsBecauseJakartaTransactionsCannotExpressThem() {
        JtaTransactionRunner runner = new JtaTransactionRunner(new RecordingTransactionManager());

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .readOnly(true)
                .build(), () -> "confirmed"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void rejectsNamedTransactionsBecauseJakartaTransactionsCannotExpressThem() {
        JtaTransactionRunner runner = new JtaTransactionRunner(new RecordingTransactionManager());

        assertThatThrownBy(() -> runner.call(TransactionOptions.builder()
                .name("confirm-order")
                .build(), () -> "confirmed"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("names");
    }

    private static final class RecordingTransactionManager implements TransactionManager {

        private boolean active;
        private boolean existingTransactionActive;
        private int begins;
        private int commits;
        private int rollbacks;
        private int rollbackOnlyMarks;
        private int suspends;
        private int resumes;
        private final List<Integer> timeoutSeconds = new ArrayList<>();
        private Transaction suspendedTransaction;
        private RuntimeException rollbackFailure;
        private RuntimeException rollbackOnlyFailure;
        private RuntimeException resumeFailure;
        private RuntimeException timeoutResetFailure;

        private void activateExistingTransaction() {
            active = true;
            existingTransactionActive = true;
        }

        private boolean isExistingTransactionActive() {
            return active && existingTransactionActive;
        }

        @Override
        public void begin() {
            active = true;
            existingTransactionActive = false;
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
            if (resumeFailure != null) {
                throw resumeFailure;
            }
            active = transaction != null;
            existingTransactionActive = transaction == suspendedTransaction;
            resumes++;
        }

        @Override
        public Transaction suspend() {
            suspendedTransaction = new Transaction() {
                @Override
                public void commit() {
                }

                @Override
                public boolean delistResource(javax.transaction.xa.XAResource xaRes, int flag) {
                    return false;
                }

                @Override
                public boolean enlistResource(javax.transaction.xa.XAResource xaRes) {
                    return false;
                }

                @Override
                public int getStatus() {
                    return Status.STATUS_ACTIVE;
                }

                @Override
                public void registerSynchronization(jakarta.transaction.Synchronization sync) {
                }

                @Override
                public void rollback() {
                }

                @Override
                public void setRollbackOnly() {
                }
            };
            active = false;
            existingTransactionActive = false;
            suspends++;
            return suspendedTransaction;
        }

        @Override
        public void rollback() {
            if (rollbackFailure != null) {
                throw rollbackFailure;
            }
            active = false;
            rollbacks++;
        }

        @Override
        public void setRollbackOnly() {
            if (rollbackOnlyFailure != null) {
                throw rollbackOnlyFailure;
            }
            rollbackOnlyMarks++;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            timeoutSeconds.add(seconds);
            if (seconds == 0 && timeoutResetFailure != null) {
                throw timeoutResetFailure;
            }
        }
    }
}
