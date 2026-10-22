package org.jfoundry.http.spring.client;

import java.lang.reflect.Method;

import org.springframework.core.MethodParameter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/// Copies the invoked HTTP interface method onto the outbound request for {@link HttpLoggingInterceptor}.
public final class HttpOperationRequestProcessor implements HttpRequestValues.Processor {

    private static final HttpOperationRequestProcessor INSTANCE = new HttpOperationRequestProcessor();

    private HttpOperationRequestProcessor() {
    }

    /// Registers this processor on a manually built HTTP service proxy.
    public static HttpServiceProxyFactory.Builder apply(HttpServiceProxyFactory.Builder builder) {
        return builder.httpRequestValuesProcessor(INSTANCE);
    }

    static void process(Method method, HttpRequestValues.Builder requestValues) {
        INSTANCE.process(method, new MethodParameter[0], new Object[0], requestValues);
    }

    @Override
    public void process(Method method, MethodParameter[] parameters, Object[] arguments,
            HttpRequestValues.Builder requestValues) {
        requestValues.configureAttributes(attributes -> attributes.putIfAbsent(
                HttpLoggingInterceptor.OPERATION_ATTRIBUTE, operationName(method)));
    }

    private static String operationName(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
