package org.jfoundry.integration.nativeimage;

import org.junit.jupiter.api.Test;
import org.jfoundry.http.spring.HttpLoggingLevel;
import org.jfoundry.web.spring.filter.HttpLoggingFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NativeSmokeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NativeSmokeApplicationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private FilterRegistrationBean<HttpLoggingFilter> httpLoggingRegistration;

    @Test
    void startsTheJfoundrySpringBootAssemblyAndExposesAReadinessEndpoint() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/jfoundry/native/ready"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ready");
        assertThat(httpLoggingRegistration.isEnabled()).isFalse();
        assertThat(ReflectionTestUtils.getField(httpLoggingRegistration.getFilter(), "level"))
                .isEqualTo(HttpLoggingLevel.NONE);
    }
}
