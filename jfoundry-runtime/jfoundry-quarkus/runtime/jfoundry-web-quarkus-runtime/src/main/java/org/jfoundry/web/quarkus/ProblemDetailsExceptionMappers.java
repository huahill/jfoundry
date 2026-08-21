package org.jfoundry.web.quarkus;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.RequestValidationProblem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Groups the precise Jakarta REST exception mappers used for JFoundry problem responses.
public final class ProblemDetailsExceptionMappers {

    private ProblemDetailsExceptionMappers() {
    }

    private abstract static class JFoundryExceptionMapper<E extends Exception> implements ExceptionMapper<E> {

        @Override
        public final Response toResponse(E exception) {
            return ProblemDetailsResponses.forException(exception);
        }
    }

    /// Maps invalid application arguments to problem responses.
    public static final class InvalidArgumentMapper extends JFoundryExceptionMapper<InvalidArgumentException> {
    }

    /// Maps missing application resources to problem responses.
    public static final class NotFoundMapper extends JFoundryExceptionMapper<NotFoundException> {
    }

    /// Maps application conflicts to problem responses.
    public static final class ConflictMapper extends JFoundryExceptionMapper<ConflictException> {
    }

    /// Maps unavailable external dependencies to problem responses.
    public static final class ExternalAccessMapper extends JFoundryExceptionMapper<ExternalAccessException> {
    }

    /// Maps domain rule violations to problem responses.
    public static final class DomainRuleViolationMapper extends JFoundryExceptionMapper<DomainRuleViolationException> {
    }

    /// Maps invalid domain state transitions to problem responses.
    public static final class DomainStateMapper extends JFoundryExceptionMapper<DomainStateException> {
    }

    /// Maps Quarkus REST request validation failures to the shared validation problem.
    public static final class RequestValidationMapper implements ExceptionMapper<ResteasyReactiveViolationException> {

        @Override
        public Response toResponse(ResteasyReactiveViolationException exception) {
            if (exception.getConstraintViolations().stream()
                    .anyMatch(ProblemDetailsExceptionMappers::isReturnValueViolation)) {
                throw exception;
            }
            return ProblemDetailsResponses.forProblem(RequestValidationProblem.create(
                    validationErrors(exception.getConstraintViolations())));
        }
    }

    /// Maps unhandled exceptions to safe internal-server-error problem responses.
    public static final class UnhandledExceptionMapper extends JFoundryExceptionMapper<Exception> {
    }

    /// Maps supported Jakarta REST failures to problem responses.
    public static final class WebApplicationMapper implements ExceptionMapper<WebApplicationException> {

        @Override
        public Response toResponse(WebApplicationException exception) {
            Response source = exception.getResponse();
            if (!ProblemCatalog.supportsHttpStatus(source.getStatus())) {
                return source;
            }
            return ProblemDetailsResponses.forHttpStatus(source.getStatus(), source.getHeaders());
        }
    }

    private static List<RequestValidationProblem.Error> validationErrors(
            Iterable<? extends ConstraintViolation<?>> violations) {
        List<RequestValidationProblem.Error> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            List<String> path = requestDocumentPath(violation);
            String detail = violation.getMessage() == null ? "is invalid" : violation.getMessage();
            errors.add(path.isEmpty()
                    ? RequestValidationProblem.Error.forRequest(detail)
                    : RequestValidationProblem.Error.atPath(path, detail));
        }
        return errors.stream()
                .sorted(Comparator.comparing((RequestValidationProblem.Error error) -> String.join("/", error.path()))
                        .thenComparing(RequestValidationProblem.Error::detail))
                .toList();
    }

    private static List<String> requestDocumentPath(ConstraintViolation<?> violation) {
        List<String> path = new ArrayList<>();
        for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() != ElementKind.PROPERTY
                    && node.getKind() != ElementKind.CONTAINER_ELEMENT
                    && node.getKind() != ElementKind.BEAN) {
                continue;
            }
            appendIterableLocation(path, node);
            if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                path.add(node.getName());
            }
        }
        return List.copyOf(path);
    }

    private static void appendIterableLocation(List<String> path, jakarta.validation.Path.Node node) {
        if (!node.isInIterable()) {
            return;
        }
        if (node.getIndex() != null) {
            path.add(node.getIndex().toString());
        } else if (node.getKey() != null) {
            path.add(node.getKey().toString());
        }
    }

    private static boolean isReturnValueViolation(ConstraintViolation<?> violation) {
        for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.RETURN_VALUE) {
                return true;
            }
        }
        return false;
    }
}
