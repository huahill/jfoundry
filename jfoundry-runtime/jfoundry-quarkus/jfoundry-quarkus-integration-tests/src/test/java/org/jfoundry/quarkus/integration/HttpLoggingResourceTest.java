package org.jfoundry.quarkus.integration;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jfoundry.http.quarkus.HttpLoggingProvider;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(HttpLoggingResourceTest.HttpLoggingTestProfile.class)
class HttpLoggingResourceTest {

    @TestHTTPResource
    URI baseUri;

    private final List<String> messages = new CopyOnWriteArrayList<>();

    private Logger logger;

    private Handler handler;

    @BeforeEach
    void captureLogs() {
        this.messages.clear();
        this.logger = Logger.getLogger(HttpLoggingProvider.class.getName());
        this.handler = new Handler() {
            private final SimpleFormatter formatter = new SimpleFormatter();

            @Override
            public void publish(LogRecord record) {
                messages.add(this.formatter.formatMessage(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        this.logger.addHandler(this.handler);
    }

    @AfterEach
    void stopCapturingLogs() {
        this.logger.removeHandler(this.handler);
    }

    @Test
    void logsARealServerExchangeOnceWithoutExposingSecrets() {
        given()
                .queryParam("access_token", "query-secret")
                .header("Authorization", "Bearer header-secret")
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Ada", "password", "request-secret"))
                .when()
                .post("/jfoundry/http-logging")
                .then()
                .statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("Ada"))
                .body("password", org.hamcrest.Matchers.equalTo("response-secret"));

        assertServerLogs();
    }

    @Test
    void automaticallyLogsMicroProfileRestClientExchanges() {
        var client = RestClientBuilder.newBuilder().baseUri(this.baseUri).build(HttpLoggingRestClient.class);

        var response = client.echo("query-secret", "Bearer header-secret",
                Map.of("name", "Ada", "password", "request-secret"));

        assertThat(response).containsEntry("name", "Ada").containsEntry("password", "response-secret");
        assertThat(this.messages).anyMatch(message -> message.startsWith("HTTP client request:"))
                .anyMatch(message -> message.startsWith("HTTP client request headers:"))
                .anyMatch(message -> message.startsWith("HTTP client request body:"))
                .anyMatch(message -> message.startsWith("HTTP client response:")
                        && message.contains("method=POST") && message.contains("status=200"))
                .anyMatch(message -> message.startsWith("HTTP client response headers:"))
                .anyMatch(message -> message.startsWith("HTTP client response body:")
                        && message.contains("method=POST") && message.contains("status=200"));
        assertNoSecrets();
    }

    private void assertServerLogs() {
        assertThat(this.messages.stream().filter(message -> message.startsWith("HTTP server request:")).toList())
                .singleElement().satisfies(message -> {
                    assertThat(message).contains("method=POST", "uri=" + this.baseUri + "jfoundry/http-logging");
                    assertThat(message).doesNotContain("access_token");
                });
        assertThat(this.messages.stream()
                .filter(message -> message.startsWith("HTTP server request headers:")).toList())
                .singleElement().satisfies(message -> assertThat(message).contains("Authorization=[<redacted>]"));
        assertThat(this.messages.stream().filter(message -> message.startsWith("HTTP server request body:")).toList())
                .singleElement().satisfies(message -> assertThat(message)
                        .contains("\"name\":\"Ada\"", "\"password\":\"<redacted>\""));
        assertThat(this.messages.stream().filter(message -> message.startsWith("HTTP server response:")).toList())
                .singleElement().satisfies(message -> assertThat(message)
                        .contains("method=POST", "uri=" + this.baseUri + "jfoundry/http-logging",
                                "status=200", "duration=").endsWith("ms"));
        assertThat(this.messages.stream()
                .filter(message -> message.startsWith("HTTP server response headers:")).toList())
                .singleElement().satisfies(message -> assertThat(message).contains("status=200", "headers="));
        assertThat(this.messages.stream().filter(message -> message.startsWith("HTTP server response body:")).toList())
                .singleElement().satisfies(message -> assertThat(message)
                        .contains("method=POST", "status=200", "\"name\":\"Ada\"",
                                "\"password\":\"<redacted>\""));
        assertNoSecrets();
    }

    private void assertNoSecrets() {
        assertThat(this.messages).allSatisfy(message -> assertThat(message)
                .doesNotContain("query-secret", "header-secret", "request-secret", "response-secret"));
    }

    public static final class HttpLoggingTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "jfoundry.web.quarkus.logging-level", "FULL",
                    "jfoundry.web.rest-client.logging-level", "FULL",
                    "quarkus.log.category.\"org.jfoundry.http.quarkus.HttpLoggingProvider\".level", "INFO");
        }
    }
}
