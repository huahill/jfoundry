package org.jfoundry.autoconfigure.webmvc;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.web.spring.filter.HttpLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/// Auto-configures inbound Servlet HTTP diagnostic logging for Spring MVC applications.
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "org.springframework.boot.web.servlet.FilterRegistrationBean",
        "org.jfoundry.web.spring.filter.HttpLoggingFilter"
})
@EnableConfigurationProperties(JfoundryWebMvcProperties.class)
public class WebMvcHttpLoggingAutoConfiguration {

    /// Default order places logging before Spring Security's normal registration.
    public static final int DEFAULT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    /// Registers the replaceable logging filter for request, async, and error dispatches.
    @Bean
    @ConditionalOnMissingFilterBean(HttpLoggingFilter.class)
    public FilterRegistrationBean<HttpLoggingFilter> jfoundryHttpLoggingFilterRegistration(
            JfoundryWebMvcProperties properties) {
        var registration = new FilterRegistrationBean<>(new HttpLoggingFilter(properties.getLoggingLevel(),
                excludedRequest(properties.getLoggingExcludedPaths())));
        registration.setName("jfoundryHttpLoggingFilter");
        registration.setOrder(DEFAULT_FILTER_ORDER);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        registration.setEnabled(properties.getLoggingLevel() != HttpLoggingLevel.NONE);
        return registration;
    }

    private static Predicate<HttpServletRequest> excludedRequest(List<String> patterns) {
        var matcher = new AntPathMatcher();
        var normalizedPatterns = patterns == null ? List.<String>of() : patterns.stream()
                .filter(StringUtils::hasText)
                .map(WebMvcHttpLoggingAutoConfiguration::normalizePattern)
                .toList();
        return request -> {
            var contextPath = request.getContextPath();
            var requestUri = request.getRequestURI();
            var hasContextPath = contextPath != null && !contextPath.isEmpty()
                    && (requestUri.equals(contextPath) || requestUri.startsWith(contextPath + "/"));
            var applicationPath = hasContextPath ? requestUri.substring(contextPath.length()) : requestUri;
            return normalizedPatterns.stream().anyMatch(pattern -> matcher.match(pattern, applicationPath));
        };
    }

    private static String normalizePattern(String pattern) {
        var trimmed = pattern.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
