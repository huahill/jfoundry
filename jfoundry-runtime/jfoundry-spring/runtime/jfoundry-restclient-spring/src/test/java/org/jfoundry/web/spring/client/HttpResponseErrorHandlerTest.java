package org.jfoundry.web.spring.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpResponseErrorHandlerTest {

    private final HttpResponseErrorHandler handler = new HttpResponseErrorHandler();

    @Test
    void retainsTheOriginalErrorBodyWithoutRewritingIt() {
        var body = """
                {"code":50000,"message":"系统异常，请联系管理员","details":null,"password":"secret"}\
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> this.handler.handleError(URI.create("https://downstream.test/orders/42"),
                HttpMethod.GET, response(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.APPLICATION_JSON, body)))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(500);
                    assertThat(exception.body()).containsExactly(body);
                    assertThat(exception).hasMessage(
                            "HTTP response failed with status 500\n" + new String(body, StandardCharsets.UTF_8));
                    assertThat(exception.publicDetail()).isEmpty();
                    assertThat(exception).hasNoCause();
                });
    }

    @Test
    void keepsAStatusOnlyMessageWhenTheErrorBodyIsEmpty() {
        assertThatThrownBy(() -> this.handler.handleError(URI.create("https://downstream.test/orders/42"),
                HttpMethod.GET, response(HttpStatus.NOT_FOUND, MediaType.APPLICATION_JSON, new byte[0])))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(exception.body()).isEmpty();
                    assertThat(exception).hasMessage("HTTP response failed with status 404");
                });
    }

    @Test
    void decodesTextWithTheResponseCharset() {
        var body = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> this.handler.handleError(URI.create("https://downstream.test/orders/42"),
                HttpMethod.GET, response(HttpStatus.BAD_REQUEST,
                        new MediaType("text", "plain", StandardCharsets.ISO_8859_1), body)))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.body()).containsExactly(body);
                    assertThat(exception).hasMessage("HTTP response failed with status 400\ncaf\u00e9");
                });
    }

    @Test
    void keepsTheCompleteBodyWhenTheDiagnosticTextIsTruncated() {
        var prefix = "{\"code\":50000,\"message\":\"系统异常，请联系管理员\",\"details\":\"";
        var body = (prefix + "x".repeat(400) + "TAIL")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> this.handler.handleError(URI.create("https://downstream.test/orders/42"),
                HttpMethod.GET, response(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.APPLICATION_JSON, body)))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.body()).containsExactly(body);
                    assertThat(exception).hasMessageContaining("\"code\":50000")
                            .hasMessageContaining("系统异常，请联系管理员")
                            .hasMessageEndingWith("... (" + body.length + " bytes)")
                            .hasMessageNotContaining("TAIL");
                });
    }

    @Test
    void previewsANonTextBodyWithoutInterpretingIt() {
        var body = new byte[] {0, 1, 2, 3, 4};

        assertThatThrownBy(() -> this.handler.handleError(URI.create("https://downstream.test/orders/42"),
                HttpMethod.GET, response(HttpStatus.BAD_GATEWAY, MediaType.APPLICATION_OCTET_STREAM, body)))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.body()).containsExactly(body);
                    assertThat(exception).hasMessage("HTTP response failed with status 502\n"
                            + new String(body, StandardCharsets.UTF_8));
                });
    }

    @Test
    void copiesTheRetainedBodyInsteadOfAliasingTheCallerArray() {
        var body = "{\"code\":50000}".getBytes(StandardCharsets.UTF_8);
        var exception = new HttpResponseException(HttpStatus.BAD_GATEWAY, body, MediaType.APPLICATION_JSON);

        body[0] = 'x';

        assertThat(exception.body()).asString(StandardCharsets.UTF_8).isEqualTo("{\"code\":50000}");
        assertThat(exception).hasMessageContaining("\"code\":50000");
    }

    private static ClientHttpResponse response(HttpStatusCode statusCode, MediaType contentType, byte[] body) {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() {
                return statusCode;
            }

            @Override
            public String getStatusText() {
                return statusCode.toString();
            }

            @Override
            public HttpHeaders getHeaders() {
                var headers = new HttpHeaders();
                headers.setContentType(contentType);
                return headers;
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public void close() {
            }
        };
    }

}
