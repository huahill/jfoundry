package org.jfoundry.helidon.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jfoundry.http.helidon.HttpLoggingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@HelidonTest
class HttpLoggingResourceTest {

    @Inject
    @Socket("@default")
    WebTarget target;

    private final List<String> messages = new CopyOnWriteArrayList<>();

    private Logger logger;

    private Handler handler;

    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        this.messages.clear();
        this.logger = Logger.getLogger(HttpLoggingProvider.class.getName());
        this.previousLevel = this.logger.getLevel();
        this.logger.setLevel(Level.INFO);
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
        this.handler.setLevel(Level.INFO);
        this.logger.addHandler(this.handler);
    }

    @AfterEach
    void stopCapturingLogs() {
        this.logger.removeHandler(this.handler);
        this.logger.setLevel(this.previousLevel);
    }

    @Test
    void logsARealServerExchangeOnceWithoutExposingSecrets() {
        try (var response = this.target.path("/jfoundry/http-logging")
                .queryParam("access_token", "query-secret")
                .request()
                .header("Authorization", "Bearer header-secret")
                .post(Entity.json(Map.of("name", "Ada", "password", "request-secret")))) {
            assertEquals(200, response.getStatus());
            assertEquals("Ada", response.readEntity(Map.class).get("name"));
        }

        assertServerLogs();
    }

    @Test
    void automaticallyLogsMicroProfileRestClientExchanges() {
        var client = RestClientBuilder.newBuilder().baseUri(this.target.getUri()).build(HttpLoggingRestClient.class);

        var response = client.echo("query-secret", "Bearer header-secret",
                Map.of("name", "Ada", "password", "request-secret"));

        assertEquals("Ada", response.get("name"));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client request:")));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client request headers:")));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client request body:")));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client response:")
                && message.contains("method=POST") && message.contains("status=200")));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client response headers:")));
        assertTrue(this.messages.stream().anyMatch(message -> message.startsWith("HTTP client response body:")
                && message.contains("method=POST") && message.contains("status=200")));
        assertNoSecrets();
    }

    private void assertServerLogs() {
        var requests = this.messages.stream().filter(message -> message.startsWith("HTTP server request:")).toList();
        assertEquals(1, requests.size());
        assertTrue(requests.getFirst().contains("method=POST"));
        assertFalse(requests.getFirst().contains("access_token"));

        var requestHeaders = this.messages.stream()
                .filter(message -> message.startsWith("HTTP server request headers:")).toList();
        assertEquals(1, requestHeaders.size());
        assertTrue(requestHeaders.getFirst().contains("Authorization=[<redacted>]"));

        var requestBodies = this.messages.stream()
                .filter(message -> message.startsWith("HTTP server request body:")).toList();
        assertEquals(1, requestBodies.size());
        assertTrue(requestBodies.getFirst().contains("\"name\":\"Ada\""));
        assertTrue(requestBodies.getFirst().contains("\"password\":\"<redacted>\""));

        var responses = this.messages.stream().filter(message -> message.startsWith("HTTP server response:")).toList();
        assertEquals(1, responses.size());
        assertTrue(responses.getFirst().contains("method=POST"));
        assertTrue(responses.getFirst().contains("status=200"));
        assertTrue(responses.getFirst().contains("durationMs="));

        var responseHeaders = this.messages.stream()
                .filter(message -> message.startsWith("HTTP server response headers:")).toList();
        assertEquals(1, responseHeaders.size());
        assertTrue(responseHeaders.getFirst().contains("status=200"));

        var responseBodies = this.messages.stream()
                .filter(message -> message.startsWith("HTTP server response body:")).toList();
        assertEquals(1, responseBodies.size());
        assertTrue(responseBodies.getFirst().contains("method=POST"));
        assertTrue(responseBodies.getFirst().contains("status=200"));
        assertTrue(responseBodies.getFirst().contains("\"password\":\"<redacted>\""));
        assertNoSecrets();
    }

    private void assertNoSecrets() {
        this.messages.forEach(message -> {
            assertFalse(message.contains("query-secret"));
            assertFalse(message.contains("header-secret"));
            assertFalse(message.contains("request-secret"));
            assertFalse(message.contains("response-secret"));
        });
    }
}
