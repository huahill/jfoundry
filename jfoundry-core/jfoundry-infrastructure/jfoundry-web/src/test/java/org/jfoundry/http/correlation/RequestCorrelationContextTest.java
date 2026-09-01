package org.jfoundry.http.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationContextTest {

    @AfterEach
    void clearContext() {
        RequestCorrelationContext.clear();
    }

    @Test
    void exposesAndClearsTheCurrentRequestContext() {
        var context = RequestCorrelationContext.of(new RequestCorrelationId("request-1"));

        assertThat(RequestCorrelationContext.current()).isEmpty();
        RequestCorrelationContext.install(context);
        assertThat(RequestCorrelationContext.current()).containsSame(context);
        RequestCorrelationContext.clear();
        assertThat(RequestCorrelationContext.current()).isEmpty();
    }

    @Test
    void doesNotShareContextBetweenThreads() throws Exception {
        RequestCorrelationContext.install(RequestCorrelationContext.of(new RequestCorrelationId("request-1")));
        var otherThreadContext = new AtomicReference<>();
        var thread = new Thread(() -> otherThreadContext.set(RequestCorrelationContext.current()));
        thread.start();
        thread.join();

        assertThat(otherThreadContext).hasValue(java.util.Optional.empty());
    }
}
