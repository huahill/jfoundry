package org.jfoundry.quarkus.web.deployment;

import jakarta.ws.rs.Priorities;
import org.jfoundry.http.quarkus.HttpLoggingProvider;
import org.jfoundry.http.quarkus.RequestCorrelationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingProcessorTest {

    @Test
    void registersAllServerLifecycleProviders() {
        var processor = new HttpLoggingProcessor();
        var request = processor.registerServerRequestFilter();
        var response = processor.registerServerResponseFilter();
        var reader = processor.registerRequestBodyInterceptor();
        var writer = processor.registerResponseBodyInterceptor();

        assertThat(request.getClassName()).isEqualTo(HttpLoggingProvider.class.getName());
        assertThat(request.isPreMatching()).isTrue();
        assertThat(request.getPriority()).isEqualTo(Priorities.USER - 200);
        assertThat(request.isRegisterAsBean()).isTrue();
        assertThat(response.getClassName()).isEqualTo(HttpLoggingProvider.class.getName());
        assertThat(response.getPriority()).isEqualTo(Priorities.USER - 200);
        assertThat(response.isRegisterAsBean()).isTrue();
        assertThat(reader.getClassName()).isEqualTo(HttpLoggingProvider.class.getName());
        assertThat(reader.getPriority()).isEqualTo(Priorities.USER - 200);
        assertThat(reader.isRegisterAsBean()).isTrue();
        assertThat(writer.getClassName()).isEqualTo(HttpLoggingProvider.class.getName());
        assertThat(writer.getPriority()).isEqualTo(Priorities.USER - 200);
        assertThat(writer.isRegisterAsBean()).isTrue();
    }

    @Test
    void registersRequestCorrelationBeforeHttpLogging() {
        var processor = new HttpLoggingProcessor();
        assertThat(processor.registerRequestCorrelationRequestFilter().getClassName())
                .isEqualTo(RequestCorrelationProvider.class.getName());
        assertThat(processor.registerRequestCorrelationRequestFilter().getPriority())
                .isEqualTo(RequestCorrelationProvider.PRIORITY);
        assertThat(processor.registerRequestCorrelationResponseFilter().getPriority())
                .isEqualTo(RequestCorrelationProvider.PRIORITY);
        assertThat(RequestCorrelationProvider.PRIORITY).isLessThan(Priorities.USER - 200);
    }
}
