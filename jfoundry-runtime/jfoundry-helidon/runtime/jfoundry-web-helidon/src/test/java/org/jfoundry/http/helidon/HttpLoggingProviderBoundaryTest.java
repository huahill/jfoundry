package org.jfoundry.http.helidon;

import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingProviderBoundaryTest {

    @Test
    void exposesOnlyServerSideJaxRsContracts() {
        assertThat(HttpLoggingProvider.class).isAssignableTo(ContainerRequestFilter.class);
        assertThat(HttpLoggingProvider.class).isAssignableTo(ContainerResponseFilter.class);
        assertThat(ClientRequestFilter.class.isAssignableFrom(HttpLoggingProvider.class)).isFalse();
        assertThat(ClientResponseFilter.class.isAssignableFrom(HttpLoggingProvider.class)).isFalse();
    }
}
