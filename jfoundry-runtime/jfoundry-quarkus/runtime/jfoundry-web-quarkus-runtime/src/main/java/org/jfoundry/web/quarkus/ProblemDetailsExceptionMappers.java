package org.jfoundry.web.quarkus;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.MethodNode;
import jakarta.validation.Path.ParameterNode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.JakartaRequestValidationErrors;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.RequestValidationProblem;

import java.lang.annotation.Annotation;
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
                    JakartaRequestValidationErrors.from(exception.getConstraintViolations(),
                            ProblemDetailsExceptionMappers::isRequestDocumentViolation)));
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
                || parameter.isAnnotationPresent(jakarta.ws.rs.container.Suspended.class)
                || java.util.Arrays.stream(parameter.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(ProblemDetailsExceptionMappers::isResteasyReactiveParameterBinding);
    }

    private static boolean isResteasyReactiveParameterBinding(Class<? extends Annotation> annotationType) {
        String name = annotationType.getName();
        return name.startsWith("org.jboss.resteasy.reactive.Rest")
                || name.equals("org.jboss.resteasy.reactive.MultipartForm");
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
