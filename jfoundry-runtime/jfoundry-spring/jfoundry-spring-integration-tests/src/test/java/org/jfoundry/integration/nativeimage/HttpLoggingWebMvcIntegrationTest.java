package org.jfoundry.integration.nativeimage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jfoundry.web.spring.filter.HttpLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NativeSmokeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jfoundry.web.server.logging-level=BASIC",
                "logging.level.org.jfoundry.web.spring.filter.HttpLoggingFilter=DEBUG"
        })
class HttpLoggingWebMvcIntegrationTest {

    private static final Pattern DURATION = Pattern.compile(".*durationMs=(\\d+).*");

    @LocalServerPort
    private int port;

    private Logger logger;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void recordsOneTerminalLogForSuccessfulAndFailedRequests() throws Exception {
        assertThat(get("/jfoundry/native/ready").statusCode()).isEqualTo(200);
        assertThat(terminalLogs("/jfoundry/native/ready")).singleElement()
                .satisfies(message -> {
                    assertThat(message).contains("status=200");
                    assertThat(duration(message)).isGreaterThanOrEqualTo(0);
                });

        logs.list.clear();
        assertThat(get("/jfoundry/native/failure").statusCode()).isEqualTo(500);
        assertThat(terminalLogs("/jfoundry/native/failure")).singleElement()
                .satisfies(message -> {
                    assertThat(message).contains("status=500");
                    assertThat(duration(message)).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    void recordsAsyncDurationAtTerminalCompletion() throws Exception {
        var response = get("/jfoundry/native/async");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("async-ready");
        assertThat(terminalLogs("/jfoundry/native/async")).singleElement()
                .satisfies(message -> {
                    assertThat(message).contains("status=200");
                    assertThat(duration(message)).isGreaterThanOrEqualTo(40);
                });
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path + "?access_token=secret"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<String> terminalLogs(String path) {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(path) && message.contains("durationMs="))
                .toList();
    }

    private static long duration(String message) {
        var matcher = DURATION.matcher(message);
        assertThat(matcher.matches()).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
