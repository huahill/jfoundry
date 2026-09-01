package org.jfoundry.http.quarkus;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationProviderBoundaryTest {

    @Test
    void exposesOnlyServerSideContractsBeforeHttpLogging() {
        assertThat(RequestCorrelationProvider.class).isAssignableTo(ContainerRequestFilter.class);
        assertThat(RequestCorrelationProvider.class).isAssignableTo(ContainerResponseFilter.class);
        assertThat(ClientRequestFilter.class.isAssignableFrom(RequestCorrelationProvider.class)).isFalse();
        assertThat(RequestCorrelationProvider.PRIORITY).isLessThan(Priorities.USER - 200);
    }
}
