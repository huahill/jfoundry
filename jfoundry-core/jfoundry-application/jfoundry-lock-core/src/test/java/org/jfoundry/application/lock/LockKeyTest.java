package org.jfoundry.application.lock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LockKeyTest {

    @Test
    void hidesValueFromStringRepresentation() {
        LockKey key = new LockKey("order-processing", "customer:42");

        assertThat(key.toString())
                .contains("order-processing")
                .doesNotContain("customer:42");
    }

    @Test
    void requiresNonBlankScopeAndValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LockKey(" ", "customer:42"));
        assertThatIllegalArgumentException().isThrownBy(() -> new LockKey("order-processing", " "));
    }
}
