package org.jfoundry.web.helidon;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jfoundry.problem.CompositeProblemMapper;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMessageResolver;

import java.util.List;
import java.util.Locale;

final class ProblemDetailsResponses {

    private static final String PROBLEM_JSON = "application/problem+json";
    static final ProblemMessageResolver MESSAGES = ProblemMessageResolver.defaults();

    private ProblemDetailsResponses() {
    }

    static Response forException(Exception exception, Locale locale) {
        CompositeProblemMapper mapper = new CompositeProblemMapper(List.of(), MESSAGES, () -> locale);
        return problem(mapper.map(exception).orElseThrow(), null, locale);
    }

    static Response forHttpStatus(int status, MultivaluedMap<String, Object> headers, Locale locale) {
        return problem(ProblemCatalog.forHttpStatus(status, locale, MESSAGES), headers, locale);
    }

    static Response forProblem(ProblemDescriptor descriptor, Locale locale) {
        return problem(descriptor, null, locale);
    }

    /// Resolves the preferred response locale from request headers, defaulting to the locale-independent root.
    static Locale requestLocale(HttpHeaders headers) {
        if (headers == null) {
            return Locale.ROOT;
        }
        List<Locale> acceptable = headers.getAcceptableLanguages();
        if (acceptable.isEmpty()) {
            return Locale.ROOT;
        }
        Locale preferred = acceptable.get(0);
        return preferred.getLanguage().isEmpty() || "*".equals(preferred.getLanguage()) ? Locale.ROOT : preferred;
    }

    private static Response problem(ProblemDescriptor descriptor, MultivaluedMap<String, Object> headers,
            Locale locale) {
        Response.ResponseBuilder response = Response.status(descriptor.status())
                .type(PROBLEM_JSON)
                .entity(ProblemDetailsRenderer.render(descriptor).getEntity());
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                        && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
                        && !HttpHeaders.CONTENT_LANGUAGE.equalsIgnoreCase(name)) {
                    values.forEach(value -> response.header(name, value));
                }
            });
        }
        if (!locale.getLanguage().isEmpty()) {
            response.header(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
        }
        return response.build();
    }
}
