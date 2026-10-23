package org.jfoundry.http.jaxrs;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.MediaType;
import org.jfoundry.http.HttpLoggingPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JaxRsHttpLoggingSupportTest {

    @Test
    void redactsNestedObjectsAndArrays() {
        var body = complete("""
                {"name":"Ada","password":"secret","items":[{"access-token":"value"},1]}
                """);

        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, body))
                .isEqualTo("{\"name\":\"Ada\",\"password\":\"<redacted>\","
                        + "\"items\":[{\"access-token\":\"<redacted>\"},1]}");
    }

    @Test
    void supportsStructuredJsonMediaTypes() {
        assertThat(JaxRsHttpLoggingSupport.describeBody(
                new MediaType("application", "problem+json"), complete("{\"token\":\"secret\"}")))
                .isEqualTo("{\"token\":\"<redacted>\"}");
    }

    @Test
    void omitsUnsafeOrUnavailableBodies() {
        var incomplete = capture("{\"name\":\"Ada\"}", HttpLoggingPolicy.DEFAULT_MAX_BODY_BYTES);

        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, incomplete))
                .isEqualTo("<not fully consumed>");
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.TEXT_PLAIN_TYPE, complete("text")))
                .isEqualTo("<omitted: content-type=text/plain>");
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, complete("invalid")))
                .isEqualTo("<omitted: invalid JSON>");
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, complete("42")))
                .isEqualTo("<omitted: JSON scalar>");
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, complete("")))
                .isEqualTo("<empty>");
    }

    @Test
    void capsCapturedBodiesWithoutChangingTheForwardedLimit() {
        var body = new JaxRsHttpLoggingSupport.BodyCapture(HttpLoggingPolicy.DEFAULT_MAX_BODY_BYTES);
        var bytes = "x".repeat(HttpLoggingPolicy.MAX_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8);

        body.capture(bytes, 0, bytes.length);
        body.markComplete();

        assertThat(body.bytes()).hasSize(HttpLoggingPolicy.MAX_BODY_BYTES);
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, body))
                .isEqualTo("<truncated at 8192 bytes>");
    }

    @Test
    void appliesTheConfiguredBodyByteLimit() {
        var limit = 16 * 1024;
        var body = new JaxRsHttpLoggingSupport.BodyCapture(limit);
        var within = ("{\"value\":\"" + "x".repeat(9_000) + "\"}").getBytes(StandardCharsets.UTF_8);

        body.capture(within, 0, within.length);
        body.markComplete();

        assertThat(body.bytes()).hasSize(within.length);
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, body))
                .startsWith("{\"value\":\"");

        var beyond = new JaxRsHttpLoggingSupport.BodyCapture(limit);
        var oversized = "x".repeat(limit + 1).getBytes(StandardCharsets.UTF_8);
        beyond.capture(oversized, 0, oversized.length);
        beyond.markComplete();
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, beyond))
                .isEqualTo("<truncated at 16384 bytes>");
    }

    @Test
    void rejectsBodyByteLimitsOutsideTheSupportedRange() {
        assertThatThrownBy(() -> new JaxRsHttpLoggingSupport.BodyCapture(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JaxRsHttpLoggingSupport.BodyCapture(
                HttpLoggingPolicy.ABSOLUTE_MAX_BODY_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JaxRsHttpLoggingSupport.BodyCapture complete(String value) {
        return complete(value, HttpLoggingPolicy.DEFAULT_MAX_BODY_BYTES);
    }

    private static JaxRsHttpLoggingSupport.BodyCapture complete(String value, int maxBodyBytes) {
        var body = capture(value, maxBodyBytes);
        body.markComplete();
        return body;
    }

    private static JaxRsHttpLoggingSupport.BodyCapture capture(String value, int maxBodyBytes) {
        var body = new JaxRsHttpLoggingSupport.BodyCapture(maxBodyBytes);
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        body.capture(bytes, 0, bytes.length);
        return body;
    }
}
