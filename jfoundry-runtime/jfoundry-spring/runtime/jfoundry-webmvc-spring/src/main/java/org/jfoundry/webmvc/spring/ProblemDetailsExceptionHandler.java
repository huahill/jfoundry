package org.jfoundry.webmvc.spring;

import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.CompositeProblemMapper;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.jfoundry.web.spring.ProblemDetailRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

/// Maps JFoundry core exceptions to Spring MVC RFC 9457 Problem Details responses.
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);
    private static final String SPRING_SECURITY_PACKAGE = "org.springframework.security.";
    private final ProblemMapper problemMapper;

    public ProblemDetailsExceptionHandler() {
        this(new CompositeProblemMapper(java.util.List.of()));
    }

    public ProblemDetailsExceptionHandler(ProblemMapper problemMapper) {
        this.problemMapper = java.util.Objects.requireNonNull(problemMapper, "problemMapper must not be null");
    }

    @ExceptionHandler(InvalidArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(InvalidArgumentException exception) {
        return problem(problemMapper.map(exception).orElseThrow());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException exception) {
        return problem(problemMapper.map(exception).orElseThrow());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException exception) {
        return problem(problemMapper.map(exception).orElseThrow());
    }

    @ExceptionHandler(ExternalAccessException.class)
    public ResponseEntity<ProblemDetail> handleExternalAccess(ExternalAccessException exception) {
        LOG.error("External access failed while processing an HTTP request", exception);
        return problem(problemMapper.map(exception).orElseThrow());
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleDomainRuleViolation(DomainRuleViolationException exception) {
        return problem(problemMapper.map(exception).orElseThrow());
    }

    @ExceptionHandler(DomainStateException.class)
    public ResponseEntity<ProblemDetail> handleDomainState(DomainStateException exception) {
        return problem(problemMapper.map(exception).orElseThrow());
    }

    /// Maps exceptions without a more specific handler through the configured problem mapper.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandled(Exception exception) {
        if (isSecurityException(exception)) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }
        LOG.error("Unhandled exception while processing an HTTP request", exception);
        return problem(problemMapper.map(exception).orElseThrow());
    }

    private static boolean isSecurityException(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().startsWith(SPRING_SECURITY_PACKAGE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDescriptor descriptor) {
        return ResponseEntity.status(HttpStatusCode.valueOf(descriptor.status()))
                .body(ProblemDetailRenderer.render(descriptor));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (!org.jfoundry.problem.ProblemCatalog.supportsHttpStatus(statusCode.value())) {
            return super.handleExceptionInternal(exception, body, headers, statusCode, request);
        }
        ProblemDescriptor descriptor = org.jfoundry.problem.ProblemCatalog.forHttpStatus(statusCode.value());
        if (statusCode.isSameCodeAs(HttpStatus.INTERNAL_SERVER_ERROR) && request instanceof ServletWebRequest) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception, WebRequest.SCOPE_REQUEST);
        }
        return super.handleExceptionInternal(exception, ProblemDetailRenderer.render(descriptor), headers,
                HttpStatusCode.valueOf(descriptor.status()), request);
    }

}
