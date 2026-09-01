package org.jfoundry.http.correlation;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RequestCorrelationOptionsTest {

    @Test
    void defaultsToIncomingAndResponsePropagation() {
        var options = RequestCorrelationOptions.defaults();

        assertThat(options.headerName()).isEqualTo("X-Request-Id");
        assertThat(options.acceptIncoming()).isTrue();
        assertThat(options.writeResponse()).isTrue();
        assertThat(options.maximumLength()).isEqualTo(64);
        assertThat(options.pathExclusion().test("/health")).isFalse();
    }

    @Test
    void rejectsInvalidHeaderAndLengthConfiguration() {
        Predicate<String> noPaths = path -> false;
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RequestCorrelationOptions("", true, true, 64, noPaths));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RequestCorrelationOptions("Bad Header", true, true, 64, noPaths));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RequestCorrelationOptions("X-Request-Id", true, true, 35, noPaths));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RequestCorrelationOptions("X-Request-Id", true, true, 65, noPaths));
        assertThatNullPointerException().isThrownBy(() ->
                new RequestCorrelationOptions("X-Request-Id", true, true, 64, null));
    }
}
