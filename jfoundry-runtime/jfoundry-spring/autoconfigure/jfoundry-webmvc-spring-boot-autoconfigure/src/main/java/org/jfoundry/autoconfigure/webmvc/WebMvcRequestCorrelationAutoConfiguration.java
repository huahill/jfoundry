package org.jfoundry.autoconfigure.webmvc;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.jfoundry.http.correlation.RequestCorrelationOptions;
import org.jfoundry.web.spring.filter.RequestCorrelationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/// Auto-configures inbound request correlation for Spring MVC applications.
@AutoConfiguration(before = WebMvcHttpLoggingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "org.springframework.boot.web.servlet.FilterRegistrationBean",
        "org.jfoundry.web.spring.filter.RequestCorrelationFilter"
})
@ConditionalOnProperty(prefix = "jfoundry.web.mvc.request-correlation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JfoundryWebMvcProperties.class)
public class WebMvcRequestCorrelationAutoConfiguration {

    /// Places request correlation before the inbound HTTP diagnostic logger.
    public static final int DEFAULT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    @ConditionalOnMissingFilterBean(RequestCorrelationFilter.class)
    FilterRegistrationBean<RequestCorrelationFilter> jfoundryRequestCorrelationFilterRegistration(
            JfoundryWebMvcProperties properties) {
        var correlation = properties.getRequestCorrelation();
        var options = new RequestCorrelationOptions(
                correlation.getHeaderName(),
                correlation.isAcceptIncoming(),
                correlation.isWriteResponse(),
                correlation.getMaximumLength(),
                excludedRequest(correlation.getExcludedPaths()));
        var registration = new FilterRegistrationBean<>(new RequestCorrelationFilter(options));
        registration.setName("jfoundryRequestCorrelationFilter");
        registration.setOrder(DEFAULT_FILTER_ORDER);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        return registration;
    }

    private static Predicate<String> excludedRequest(List<String> patterns) {
        var matcher = new AntPathMatcher();
        var normalizedPatterns = patterns == null ? List.<String>of() : patterns.stream()
                .filter(StringUtils::hasText)
                .map(WebMvcRequestCorrelationAutoConfiguration::normalizePattern)
                .toList();
        return path -> normalizedPatterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private static String normalizePattern(String pattern) {
        var trimmed = pattern.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
