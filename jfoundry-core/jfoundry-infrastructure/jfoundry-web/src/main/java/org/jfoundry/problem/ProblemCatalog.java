package org.jfoundry.problem;

import org.jfoundry.application.exception.ApplicationException;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// Maps JFoundry and standard HTTP failures to stable problem descriptors.
/// Localized overloads resolve framework strings and exception message codes for the supplied locale;
/// unresolved codes and missing translations fall back to the built-in English text.
public final class ProblemCatalog {

    private static final String TYPE_PREFIX = "urn:jfoundry:problem:";

    private ProblemCatalog() {
    }

    /// Maps the exception with built-in English text.
    public static ProblemDescriptor forException(Exception exception) {
        return forException(exception, Locale.ROOT, ProblemMessageResolver.unresolved());
    }

    /// Maps the exception with localized framework strings and localized details for coded exceptions.
    public static ProblemDescriptor forException(Exception exception, Locale locale,
            ProblemMessageResolver messages) {
        if (exception instanceof InvalidArgumentException) {
            return problem(400, title("invalid-argument", "Invalid argument", locale, messages), "invalid-argument",
                    codedDetail(exception, locale, messages), extensions(exception));
        }
        if (exception instanceof NotFoundException) {
            return problem(404, title("not-found", "Not found", locale, messages), "not-found",
                    codedDetail(exception, locale, messages), extensions(exception));
        }
        if (exception instanceof ConflictException) {
            return problem(409, title("conflict", "Conflict", locale, messages), "conflict",
                    codedDetail(exception, locale, messages), extensions(exception));
        }
        if (exception instanceof ExternalAccessException externalAccessException) {
            return problem(503, title("external-access", "Service temporarily unavailable", locale, messages),
                    "external-access", externalAccessDetail(externalAccessException, locale, messages),
                    externalAccessExtensions(externalAccessException));
        }
        if (exception instanceof DomainRuleViolationException) {
            return problem(422, title("domain-rule-violation", "Domain rule violation", locale, messages),
                    "domain-rule-violation", codedDetail(exception, locale, messages), extensions(exception));
        }
        if (exception instanceof DomainStateException) {
            return problem(409, title("domain-state", "Domain state conflict", locale, messages), "domain-state",
                    codedDetail(exception, locale, messages), extensions(exception));
        }
        throw new IllegalArgumentException("Unsupported JFoundry exception: " + exception.getClass().getName());
    }

    /// Maps the HTTP status with built-in English text.
    public static ProblemDescriptor forHttpStatus(int status) {
        return forHttpStatus(status, Locale.ROOT, ProblemMessageResolver.unresolved());
    }

    /// Maps the HTTP status with localized framework title and detail.
    public static ProblemDescriptor forHttpStatus(int status, Locale locale, ProblemMessageResolver messages) {
        return switch (status) {
            case 400 -> problem(400, title("http-bad-request", "Bad request", locale, messages),
                    "http-bad-request", detail("http-bad-request", "The request is invalid.", locale, messages));
            case 404 -> problem(404, title("http-not-found", "Not found", locale, messages), "http-not-found",
                    detail("http-not-found", "The requested resource was not found.", locale, messages));
            case 405 -> problem(405, title("http-method-not-allowed", "Method not allowed", locale, messages),
                    "http-method-not-allowed",
                    detail("http-method-not-allowed", "The HTTP method is not allowed for this resource.",
                            locale, messages));
            case 406 -> problem(406, title("http-not-acceptable", "Not acceptable", locale, messages),
                    "http-not-acceptable",
                    detail("http-not-acceptable", "The requested representation is not available.", locale, messages));
            case 413 -> problem(413, title("http-payload-too-large", "Payload too large", locale, messages),
                    "http-payload-too-large",
                    detail("http-payload-too-large", "The request payload is too large.", locale, messages));
            case 415 -> problem(415, title("http-unsupported-media-type", "Unsupported media type", locale, messages),
                    "http-unsupported-media-type",
                    detail("http-unsupported-media-type", "The request media type is not supported.", locale,
                            messages));
            case 503 -> problem(503, title("http-service-unavailable", "Service unavailable", locale, messages),
                    "http-service-unavailable",
                    detail("http-service-unavailable", "The service is temporarily unavailable.", locale, messages));
            default -> problem(500, title("http-internal-error", "Internal server error", locale, messages),
                    "http-internal-error",
                    detail("http-internal-error", "The server failed to process the request.", locale, messages));
        };
    }

    /// Returns whether the status has shared JFoundry problem semantics.
    public static boolean supportsHttpStatus(int status) {
        return switch (status) {
            case 400, 404, 405, 406, 413, 415, 503 -> true;
            default -> false;
        };
    }

    private static String title(String type, String englishFallback, Locale locale,
            ProblemMessageResolver messages) {
        return messages.resolve("problem." + type + ".title", locale, List.of()).orElse(englishFallback);
    }

    private static String detail(String type, String englishFallback, Locale locale,
            ProblemMessageResolver messages) {
        return messages.resolve("problem." + type + ".detail", locale, List.of()).orElse(englishFallback);
    }

    private static String codedDetail(Exception exception, Locale locale, ProblemMessageResolver messages) {
        return codeOf(exception)
                .flatMap(code -> messages.resolve(code, locale, argumentsOf(exception)))
                .orElseGet(exception::getMessage);
    }

    private static String externalAccessDetail(ExternalAccessException exception, Locale locale,
            ProblemMessageResolver messages) {
        Optional<String> codedDetail = exception.publicDetailCode()
                .flatMap(code -> messages.resolve(code, locale, exception.publicDetailArguments()));
        return codedDetail
                .or(exception::publicDetail)
                .orElseGet(() -> detail("external-access", "The requested operation is temporarily unavailable.",
                        locale, messages));
    }

    private static Map<String, Object> extensions(Exception exception) {
        return codeOf(exception)
                .map(code -> Map.<String, Object>of("code", code, "args", argumentsOf(exception)))
                .orElse(Map.of());
    }

    private static Map<String, Object> externalAccessExtensions(ExternalAccessException exception) {
        return exception.publicDetailCode()
                .map(code -> Map.<String, Object>of("code", code, "args", exception.publicDetailArguments()))
                .orElseGet(() -> extensions(exception));
    }

    private static Optional<String> codeOf(Exception exception) {
        if (exception instanceof DomainException domainException) {
            return domainException.problemCode();
        }
        if (exception instanceof ApplicationException applicationException) {
            return applicationException.problemCode();
        }
        return Optional.empty();
    }

    private static List<Object> argumentsOf(Exception exception) {
        if (exception instanceof DomainException domainException) {
            return domainException.problemArguments();
        }
        if (exception instanceof ApplicationException applicationException) {
            return applicationException.problemArguments();
        }
        return List.of();
    }

    private static ProblemDescriptor problem(int status, String title, String type, String detail) {
        return problem(status, title, type, detail, Map.of());
    }

    private static ProblemDescriptor problem(int status, String title, String type, String detail,
            Map<String, Object> extensions) {
        return new ProblemDescriptor(URI.create(TYPE_PREFIX + type), title, status, detail, extensions);
    }
}
