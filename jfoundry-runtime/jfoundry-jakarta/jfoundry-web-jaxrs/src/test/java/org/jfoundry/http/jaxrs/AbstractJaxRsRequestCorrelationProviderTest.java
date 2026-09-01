package org.jfoundry.http.jaxrs;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.Priorities;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jfoundry.http.correlation.RequestCorrelationContext;
import org.jfoundry.http.correlation.RequestCorrelationOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractJaxRsRequestCorrelationProviderTest {

    @AfterEach
    void clearContext() {
        RequestCorrelationContext.clear();
    }

    @Test
    void isAserverSideRequestAndResponseProvider() {
        assertThat(AbstractJaxRsRequestCorrelationProvider.class)
                .isAssignableTo(ContainerRequestFilter.class)
                .isAssignableTo(ContainerResponseFilter.class);
        assertThat(AbstractJaxRsRequestCorrelationProvider.PRIORITY).isEqualTo(Priorities.USER - 300);
    }

    @Test
    void restoresPreviousContextAndUsesRequestOptionsAtResponseTime() throws Exception {
        var previous = RequestCorrelationContext.of(new org.jfoundry.http.correlation.RequestCorrelationId("outer"));
        RequestCorrelationContext.install(previous);
        var provider = new TestProvider();
        var requestState = new HashMap<String, Object>();
        var request = requestContext(requestState, "inner");
        var responseHeaders = new MultivaluedHashMap<String, Object>();
        var response = responseContext(responseHeaders);

        provider.filter(request);
        assertThat(RequestCorrelationContext.current()).isNotEmpty()
                .get().extracting(context -> context.id().value()).isEqualTo("inner");
        provider.filter(request, response);

        assertThat(responseHeaders.getFirst("X-Request-Id")).isEqualTo("inner");
        assertThat(RequestCorrelationContext.current()).containsSame(previous);
        RequestCorrelationContext.clear();
    }

    @Test
    void matchesAntStyleExclusions() {
        var excluded = RequestCorrelationPathMatcher.predicate(List.of("/actuator/**", "/orders/?/items"));
        assertThat(excluded.test("/actuator/health")).isTrue();
        assertThat(excluded.test("/orders/1/items")).isTrue();
        assertThat(excluded.test("/orders/12/items")).isFalse();
    }

    private static ContainerRequestContext requestContext(Map<String, Object> properties, String header) {
        var uriInfo = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[]{UriInfo.class},
                (proxy, method, args) -> method.getName().equals("getRequestUri")
                        ? URI.create("https://example.test/orders") : defaultValue(method.getReturnType()));
        return (ContainerRequestContext) Proxy.newProxyInstance(ContainerRequestContext.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUriInfo" -> uriInfo;
                    case "getHeaderString" -> header;
                    case "setProperty" -> properties.put((String) args[0], args[1]);
                    case "getProperty" -> properties.get(args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ContainerResponseContext responseContext(MultivaluedHashMap<String, Object> headers) {
        return (ContainerResponseContext) Proxy.newProxyInstance(ContainerResponseContext.class.getClassLoader(),
                new Class<?>[]{ContainerResponseContext.class}, (proxy, method, args) -> method.getName().equals("getHeaders")
                        ? headers : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class TestProvider extends AbstractJaxRsRequestCorrelationProvider {
        private TestProvider() {
            super(() -> new RequestCorrelationOptions("X-Request-Id", true, true, 64, path -> false));
        }
    }
}
