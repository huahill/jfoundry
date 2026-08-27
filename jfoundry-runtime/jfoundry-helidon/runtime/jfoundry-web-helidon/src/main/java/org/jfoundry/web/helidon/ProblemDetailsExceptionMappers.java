package org.jfoundry.web.helidon;

import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.MethodNode;
import jakarta.validation.Path.ParameterNode;
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
import org.jfoundry.problem.JakartaRequestValidationErrors;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.RequestValidationProblem;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
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
                    JakartaRequestValidationErrors.from(exception.getConstraintViolations(),
                            ProblemDetailsExceptionMappers::isRequestDocumentViolation)));
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

    private static boolean isRequestDocumentViolation(ConstraintViolation<?> violation) {
        MethodNode methodNode = null;
        ParameterNode parameterNode = null;
        for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD) {
                methodNode = node.as(MethodNode.class);
            } else if (node.getKind() == ElementKind.PARAMETER) {
                parameterNode = node.as(ParameterNode.class);
            }
        }
        if (methodNode == null || parameterNode == null || violation.getRootBeanClass() == null) {
            return false;
        }
        List<Method> methods = matchingMethods(violation.getRootBeanClass(), methodNode.getName(),
                methodNode.getParameterTypes());
        int parameterIndex = parameterNode.getParameterIndex();
        return !methods.isEmpty() && methods.stream()
                .allMatch(method -> parameterIndex < method.getParameterCount()
                        && !hasRequestParameterBinding(method.getParameters()[parameterIndex]));
    }

    private static List<Method> matchingMethods(Class<?> type, String name, List<Class<?>> parameterTypes) {
        List<Method> methods = new ArrayList<>();
        collectMatchingMethods(type, name, parameterTypes.toArray(Class[]::new), methods);
        return List.copyOf(methods);
    }

    private static void collectMatchingMethods(Class<?> type, String name, Class<?>[] parameterTypes,
                                               List<Method> methods) {
        if (type == null || type == Object.class) {
            return;
        }
        try {
            methods.add(type.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException ignored) {
            // Continue through inherited resource contracts.
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            collectMatchingMethods(interfaceType, name, parameterTypes, methods);
        }
        collectMatchingMethods(type.getSuperclass(), name, parameterTypes, methods);
    }

    private static boolean hasRequestParameterBinding(Parameter parameter) {
        return parameter.isAnnotationPresent(jakarta.ws.rs.BeanParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.CookieParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.FormParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.HeaderParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.MatrixParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.PathParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.QueryParam.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.core.Context.class)
                || parameter.isAnnotationPresent(jakarta.ws.rs.container.Suspended.class);
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
