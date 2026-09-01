package org.jfoundry.web.spring.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jfoundry.http.correlation.RequestCorrelationContext;
import org.jfoundry.http.correlation.RequestCorrelationId;
import org.jfoundry.http.correlation.RequestCorrelationOptions;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/// Establishes and projects a validated request correlation identifier for Spring MVC requests.
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "request_id";

    private final RequestCorrelationOptions options;

    /// Creates a filter using the default request-correlation policy.
    public RequestCorrelationFilter() {
        this(RequestCorrelationOptions.defaults());
    }

    /// Creates a filter with the supplied request-correlation policy.
    public RequestCorrelationFilter(RequestCorrelationOptions options) {
        this.options = java.util.Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return options.isPathExcluded(applicationPath(request));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var previousContext = RequestCorrelationContext.current();
        var previousMdcValue = MDC.get(MDC_KEY);
        var id = incomingId(request).orElseGet(RequestCorrelationId::generate);
        var context = RequestCorrelationContext.of(id);
        request.setAttribute(RequestCorrelationContext.ATTRIBUTE_NAME, context);
        RequestCorrelationContext.install(context);
        MDC.put(MDC_KEY, id.value());
        try {
            if (options.writeResponse()) {
                response.setHeader(options.headerName(), id.value());
            }
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(previousMdcValue);
            RequestCorrelationContext.install(previousContext.orElse(null));
        }
    }

    private Optional<RequestCorrelationId> incomingId(HttpServletRequest request) {
        if (!options.acceptIncoming()) {
            return Optional.empty();
        }
        return RequestCorrelationId.parse(request.getHeader(options.headerName()), options.maximumLength());
    }

    private static String applicationPath(HttpServletRequest request) {
        var requestUri = request.getRequestURI();
        var contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()
                && (requestUri.equals(contextPath) || requestUri.startsWith(contextPath + "/"))) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private static void restoreMdc(String previousMdcValue) {
        if (previousMdcValue == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previousMdcValue);
        }
    }
}
