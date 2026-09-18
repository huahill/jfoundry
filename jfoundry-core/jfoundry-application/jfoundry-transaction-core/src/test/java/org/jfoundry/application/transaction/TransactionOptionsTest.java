package org.jfoundry.application.transaction;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TransactionOptionsTest {

    @Test
    void createsDefaultRequiredWriteTransactionOptions() {
        TransactionOptions options = TransactionOptions.defaults();

        assertThat(options.name()).isEmpty();
        assertThat(options.readOnly()).isFalse();
        assertThat(options.timeout()).isEmpty();
        assertThat(options.propagation()).isEqualTo(TransactionPropagation.REQUIRED);
    }

    @Test
    void createsReadOnlyOptionsWithNameTimeoutAndPropagation() {
        TransactionOptions options = TransactionOptions.builder()
                .name("load-documents")
                .readOnly(true)
                .timeout(Duration.ofSeconds(5))
                .propagation(TransactionPropagation.SUPPORTS)
                .build();

        assertThat(options.name()).contains("load-documents");
        assertThat(options.readOnly()).isTrue();
        assertThat(options.timeout()).contains(Duration.ofSeconds(5));
        assertThat(options.propagation()).isEqualTo(TransactionPropagation.SUPPORTS);
    }

    @Test
    void rejectsNullOptionals() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TransactionOptions(null, false, Optional.empty(), TransactionPropagation.REQUIRED))
                .withMessage("name must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new TransactionOptions(Optional.empty(), false, null, TransactionPropagation.REQUIRED))
                .withMessage("timeout must not be null");
    }

}
