package io.huahill.jfoundry.application.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendResultTest {

    @Test
    void okFactoryProducesSuccessWithNullError() {
        SendResult result = SendResult.ok();

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void failFactoryProducesFailureWithMessage() {
        SendResult result = SendResult.fail("connection refused");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("connection refused");
    }
}
