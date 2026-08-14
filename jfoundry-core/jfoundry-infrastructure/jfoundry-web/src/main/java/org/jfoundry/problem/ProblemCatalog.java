package org.jfoundry.problem;

import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;

import java.net.URI;

/// Maps JFoundry and standard HTTP failures to stable problem descriptors.
public final class ProblemCatalog {

    private static final String TYPE_PREFIX = "urn:jfoundry:problem:";

    private ProblemCatalog() {
    }

    public static ProblemDescriptor forException(Exception exception) {
        if (exception instanceof InvalidArgumentException) {
            return problem(400, "INVALID_ARGUMENT", "Invalid argument", "invalid-argument", exception.getMessage());
        }
        if (exception instanceof NotFoundException) {
            return problem(404, "NOT_FOUND", "Not found", "not-found", exception.getMessage());
        }
        if (exception instanceof ConflictException) {
            return problem(409, "CONFLICT", "Conflict", "conflict", exception.getMessage());
        }
        if (exception instanceof ExternalAccessException) {
            return problem(503, "EXTERNAL_ACCESS", "Service temporarily unavailable", "external-access",
                    "The requested operation is temporarily unavailable.");
        }
        if (exception instanceof DomainRuleViolationException) {
            return problem(422, "DOMAIN_RULE_VIOLATION", "Domain rule violation", "domain-rule-violation",
                    exception.getMessage());
        }
        if (exception instanceof DomainStateException) {
            return problem(409, "DOMAIN_STATE", "Domain state conflict", "domain-state", exception.getMessage());
        }
        throw new IllegalArgumentException("Unsupported JFoundry exception: " + exception.getClass().getName());
    }

    public static ProblemDescriptor forHttpStatus(int status) {
        return switch (status) {
            case 400 -> problem(400, "HTTP_BAD_REQUEST", "Bad request", "http-bad-request", "The request is invalid.");
            case 404 -> problem(404, "HTTP_NOT_FOUND", "Not found", "http-not-found", "The requested resource was not found.");
            case 405 -> problem(405, "HTTP_METHOD_NOT_ALLOWED", "Method not allowed", "http-method-not-allowed",
                    "The HTTP method is not allowed for this resource.");
            case 406 -> problem(406, "HTTP_NOT_ACCEPTABLE", "Not acceptable", "http-not-acceptable",
                    "The requested representation is not available.");
            case 413 -> problem(413, "HTTP_PAYLOAD_TOO_LARGE", "Payload too large", "http-payload-too-large",
                    "The request payload is too large.");
            case 415 -> problem(415, "HTTP_UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", "http-unsupported-media-type",
                    "The request media type is not supported.");
            case 503 -> problem(503, "HTTP_SERVICE_UNAVAILABLE", "Service unavailable", "http-service-unavailable",
                    "The service is temporarily unavailable.");
            default -> problem(500, "HTTP_INTERNAL_ERROR", "Internal server error", "http-internal-error",
                    "The server failed to process the request.");
        };
    }

    /// Returns whether the status has shared JFoundry problem semantics.
    public static boolean supportsHttpStatus(int status) {
        return switch (status) {
            case 400, 404, 405, 406, 413, 415, 503 -> true;
            default -> false;
        };
    }

    private static ProblemDescriptor problem(int status, String code, String title, String type, String detail) {
        return new ProblemDescriptor(status, code, title, URI.create(TYPE_PREFIX + type), detail);
    }
}
