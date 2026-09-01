package org.jfoundry.http.correlation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RequestCorrelationIdTest {

    @Test
    void parsesOnlyPrintableCorrelationTokens() {
        assertThat(RequestCorrelationId.parse("abc-123._~")).hasValueSatisfying(id ->
                assertThat(id.value()).isEqualTo("abc-123._~"));
        assertThat(RequestCorrelationId.parse("" )).isEmpty();
        assertThat(RequestCorrelationId.parse("contains space")).isEmpty();
        assertThat(RequestCorrelationId.parse("bad\r\nvalue")).isEmpty();
        assertThat(RequestCorrelationId.parse("bad/slash")).isEmpty();
        assertThat(RequestCorrelationId.parse("x".repeat(65))).isEmpty();
    }

    @Test
    void appliesConfiguredMaximumLength() {
        assertThat(RequestCorrelationId.parse("short", 5)).isPresent();
        assertThat(RequestCorrelationId.parse("longer", 5)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> RequestCorrelationId.parse("x", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> RequestCorrelationId.parse("x", 65));
    }

    @Test
    void generatedIdsAreValidUuidTokens() {
        var generated = RequestCorrelationId.generate();
        assertThat(generated.value()).hasSize(36);
        assertThat(RequestCorrelationId.parse(generated.value())).contains(generated);
    }

    @Test
    void directConstructionCannotBypassValidation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestCorrelationId("bad\nvalue"));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestCorrelationId("x".repeat(65)));
    }
}
