package org.jfoundry.web.spring.filter;

import org.jfoundry.http.correlation.RequestCorrelationContext;
import org.jfoundry.http.correlation.RequestCorrelationOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    @AfterEach
    void clearThreadState() {
        RequestCorrelationContext.clear();
        MDC.clear();
    }

    @Test
    void participatesInAsyncAndErrorDispatches() {
        var filter = new RequestCorrelationFilter();
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
        assertThat(filter.shouldNotFilterErrorDispatch()).isFalse();
    }

    @Test
    void usesValidIncomingIdInContextResponseAndMdc() throws Exception {
        var filter = new RequestCorrelationFilter(RequestCorrelationOptions.defaults());
        var request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-Request-Id", "client-123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(RequestCorrelationContext.current()).get()
                    .extracting(context -> context.id().value()).isEqualTo("client-123");
            assertThat(actualRequest.getAttribute(RequestCorrelationContext.ATTRIBUTE_NAME)).isNotNull();
            assertThat(MDC.get("request_id")).isEqualTo("client-123");
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-123");
        assertThat(RequestCorrelationContext.current()).isEmpty();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void generatesIdForInvalidIncomingValueAndCanDisableResponseHeader() throws Exception {
        var options = new RequestCorrelationOptions("X-Request-Id", true, false, 64, path -> false);
        var filter = new RequestCorrelationFilter(options);
        var request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-Request-Id", "bad\nvalue");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            var context = (RequestCorrelationContext) actualRequest.getAttribute(
                    RequestCorrelationContext.ATTRIBUTE_NAME);
            assertThat(context.id().value()).hasSize(36);
        });

        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    @Test
    void excludesConfiguredPathWithoutInstallingState() throws Exception {
        var options = new RequestCorrelationOptions("X-Request-Id", true, true, 64,
                "/health"::equals);
        var filter = new RequestCorrelationFilter(options);
        var request = new MockHttpServletRequest("GET", "/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(RequestCorrelationContext.current()).isEmpty();
            assertThat(MDC.get("request_id")).isNull();
        });

        assertThat(response.getHeader("X-Request-Id")).isNull();
        assertThat(request.getAttribute(RequestCorrelationContext.ATTRIBUTE_NAME)).isNull();
    }
}
