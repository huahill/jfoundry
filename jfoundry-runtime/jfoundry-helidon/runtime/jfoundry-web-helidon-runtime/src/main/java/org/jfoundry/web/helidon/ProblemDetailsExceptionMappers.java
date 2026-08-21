package org.jfoundry.web.helidon;

import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
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

    @Provider
    public static final class InvalidArgumentMapper extends JFoundryExceptionMapper<InvalidArgumentException> { }
    @Provider
    public static final class NotFoundMapper extends JFoundryExceptionMapper<NotFoundException> { }
    @Provider
    public static final class ConflictMapper extends JFoundryExceptionMapper<ConflictException> { }
    @Provider
    public static final class ExternalAccessMapper extends JFoundryExceptionMapper<ExternalAccessException> { }
    @Provider
    public static final class DomainRuleViolationMapper extends JFoundryExceptionMapper<DomainRuleViolationException> { }
    @Provider
    public static final class DomainStateMapper extends JFoundryExceptionMapper<DomainStateException> { }
    @Provider
    @Priority(Priorities.USER - 100)
    public static final class RequestValidationMapper implements ExceptionMapper<ConstraintViolationException> {
        @Override
        public Response toResponse(ConstraintViolationException exception) {
            if (!isResourceRequestValidation(exception)) {
                throw exception;
            }
            return ProblemDetailsResponses.forProblem(RequestValidationProblem.create(
                    validationErrors(exception.getConstraintViolations())));
        }
    }
    @Provider
    public static final class UnhandledExceptionMapper extends JFoundryExceptionMapper<Exception> { }

    @Provider
    public static final class WebApplicationMapper implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException exception) {
            Response source = exception.getResponse();
            return ProblemCatalog.supportsHttpStatus(source.getStatus())
                    ? ProblemDetailsResponses.forHttpStatus(source.getStatus(), source.getHeaders())
                    : source;
        }
    }

    private static boolean isResourceRequestValidation(ConstraintViolationException exception) {
        if (exception.getConstraintViolations().isEmpty()) {
            return false;
        }
        return exception.getConstraintViolations().stream()
                .allMatch(violation -> isJaxRsResource(violation.getRootBeanClass())
                        && !isReturnValueViolation(violation));
    }

    private static boolean isJaxRsResource(Class<?> type) {
        if (type == null || type == Object.class) {
            return false;
        }
        if (type.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
            return true;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (isJaxRsResource(interfaceType)) {
                return true;
            }
        }
        return isJaxRsResource(type.getSuperclass());
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
