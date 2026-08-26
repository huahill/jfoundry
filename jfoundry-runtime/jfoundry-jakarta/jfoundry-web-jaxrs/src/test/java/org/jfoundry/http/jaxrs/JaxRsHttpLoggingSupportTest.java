package org.jfoundry.http.jaxrs;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.MediaType;
import org.jfoundry.http.HttpLoggingPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        var incomplete = capture("{\"name\":\"Ada\"}");

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
        var body = new JaxRsHttpLoggingSupport.BodyCapture();
        var bytes = "x".repeat(HttpLoggingPolicy.MAX_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8);

        body.capture(bytes, 0, bytes.length);
        body.markComplete();

        assertThat(body.bytes()).hasSize(HttpLoggingPolicy.MAX_BODY_BYTES);
        assertThat(JaxRsHttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON_TYPE, body))
                .isEqualTo("<truncated at 8192 bytes>");
    }

    private static JaxRsHttpLoggingSupport.BodyCapture complete(String value) {
        var body = capture(value);
        body.markComplete();
        return body;
    }

    private static JaxRsHttpLoggingSupport.BodyCapture capture(String value) {
        var body = new JaxRsHttpLoggingSupport.BodyCapture();
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        body.capture(bytes, 0, bytes.length);
        return body;
    }
}
