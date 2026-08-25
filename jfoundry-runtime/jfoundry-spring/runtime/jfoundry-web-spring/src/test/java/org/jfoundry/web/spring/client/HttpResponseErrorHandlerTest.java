package org.jfoundry.web.spring.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.InputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpResponseErrorHandlerTest {

    private final HttpResponseErrorHandler handler = new HttpResponseErrorHandler();

    @Test
    void translatesAnErrorStatusWithoutReadingTheResponseBody() {
        assertThatThrownBy(() -> handler.handleError(URI.create("https://downstream.test/orders/42"), HttpMethod.GET,
                responseWithUnreadableBody(HttpStatus.NOT_FOUND)))
                .isInstanceOfSatisfying(HttpResponseException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(exception).hasNoCause();
                    assertThat(exception).hasMessage("HTTP response failed with status 404");
                });
    }

    private static ClientHttpResponse responseWithUnreadableBody(HttpStatusCode statusCode) {
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
                return HttpHeaders.EMPTY;
            }

            @Override
            public InputStream getBody() {
                throw new AssertionError("The response body must not be read");
            }

            @Override
            public void close() {
            }
        };
    }
}
