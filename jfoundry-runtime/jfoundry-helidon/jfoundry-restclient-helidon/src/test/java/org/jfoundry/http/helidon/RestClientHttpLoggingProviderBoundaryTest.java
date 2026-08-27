package org.jfoundry.http.helidon;

import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientHttpLoggingProviderBoundaryTest {

    @Test
    void exposesOnlyClientSideJaxRsContracts() {
        assertThat(RestClientHttpLoggingProvider.class).isAssignableTo(ClientRequestFilter.class);
        assertThat(RestClientHttpLoggingProvider.class).isAssignableTo(ClientResponseFilter.class);
        assertThat(ContainerRequestFilter.class.isAssignableFrom(RestClientHttpLoggingProvider.class)).isFalse();
        assertThat(ContainerResponseFilter.class.isAssignableFrom(RestClientHttpLoggingProvider.class)).isFalse();
    }
}
