package org.jfoundry.quarkus.restclient.deployment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientHttpLoggingProcessorTest {

    @Test
    void registersTheRestClientBuilderListener() {
        var registration = new RestClientHttpLoggingProcessor().registerRestClientBuilderListener();

        assertThat(registration.serviceDescriptorFile())
                .isEqualTo("META-INF/services/org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener");
        assertThat(registration.providers())
                .containsExactly(RestClientHttpLoggingProcessor.REST_CLIENT_BUILDER_LISTENER);
    }
}
