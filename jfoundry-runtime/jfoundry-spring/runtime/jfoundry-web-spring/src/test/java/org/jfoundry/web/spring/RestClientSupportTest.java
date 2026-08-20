package org.jfoundry.web.spring;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestClientSupportTest {

    @Test
    void configuresTheSuppliedRestClientBuilder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI()).hasToString("https://downstream.test/orders/42"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("ignored response body"));
        RestClient client = RestClientSupport.configure(builder).build();

        assertThatThrownBy(() -> client.get().uri("https://downstream.test/orders/42").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpResponseException.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value()));
        server.verify();
    }

    @Test
    void preservesTranslatedResponseFailures() {
        HttpResponseException expected = new HttpResponseException(org.springframework.http.HttpStatus.BAD_GATEWAY);

        assertThatThrownBy(() -> RestClientSupport.execute(() -> {
            throw expected;
        })).isSameAs(expected);
    }

    @Test
    void translatesTransportFailuresWhileRetainingTheOriginalCauseForServerLogging() {
        assertFailureKind(new ResourceAccessException("connect", new ConnectException("private endpoint")),
                HttpRequestFailureKind.CONNECTION);
        assertFailureKind(new ResourceAccessException("timeout", new SocketTimeoutException("request timeout")),
                HttpRequestFailureKind.TIMEOUT);
        assertFailureKind(new ResourceAccessException("tls", new SSLException("certificate")),
                HttpRequestFailureKind.TLS);
    }

    @Test
    void translatesResponseDecodingFailuresWhileRetainingTheOriginalCauseForServerLogging() {
        HttpInputMessage inputMessage = new HttpInputMessage() {
            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }

            @Override
            public ByteArrayInputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
        RestClientException source = new RestClientException("decode",
                new HttpMessageNotReadableException("invalid response", inputMessage));

        assertFailureKind(source, HttpRequestFailureKind.RESPONSE_DECODING);
    }

    @Test
    void classifiesOtherRestClientFailuresAsUnknown() {
        assertFailureKind(new RestClientException("unexpected"), HttpRequestFailureKind.UNKNOWN);
    }

    private static void assertFailureKind(RestClientException source, HttpRequestFailureKind expectedKind) {
        assertThatThrownBy(() -> RestClientSupport.execute(() -> {
            throw source;
        })).isInstanceOfSatisfying(HttpRequestException.class, exception -> {
            assertThat(exception.failureKind()).isEqualTo(expectedKind);
            assertThat(exception).hasCause(source);
        });
    }
}
