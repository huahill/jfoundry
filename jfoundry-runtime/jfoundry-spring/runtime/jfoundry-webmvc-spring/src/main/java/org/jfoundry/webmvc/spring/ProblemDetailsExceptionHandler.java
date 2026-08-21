package org.jfoundry.webmvc.spring;

import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.CompositeProblemMapper;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.jfoundry.problem.RequestValidationProblem;
import org.jfoundry.web.spring.ProblemDetailRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

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
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode statusCode,
                                                                  WebRequest request) {
        List<RequestValidationProblem.Error> errors = exception.getBindingResult().getAllErrors().stream()
                .map(ProblemDetailsExceptionHandler::validationError)
                .toList();
        ProblemDescriptor descriptor = RequestValidationProblem.create(errors);
        return super.handleExceptionInternal(exception, ProblemDetailRenderer.render(descriptor), headers,
                HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
                                                        HttpHeaders headers,
                                                        HttpStatusCode statusCode,
                                                        WebRequest request) {
        String propertyName = exception.getPropertyName();
        String detail = propertyName == null
                ? "Failed to convert a request value."
                : "Failed to convert request value for '" + propertyName + "'.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusCode, detail);
        return super.handleExceptionInternal(exception, problem, headers, statusCode, request);
    }

    private static RequestValidationProblem.Error validationError(ObjectError error) {
        String detail = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        if (error instanceof FieldError fieldError) {
            return RequestValidationProblem.Error.atPath(fieldPath(fieldError.getField()), detail);
        }
        return RequestValidationProblem.Error.forRequest(detail);
    }

    private static List<String> fieldPath(String field) {
        List<String> path = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean bracketed = false;
        for (int index = 0; index < field.length(); index++) {
            char character = field.charAt(index);
            if (character == '.' && !bracketed) {
                appendPathToken(path, token);
            } else if (character == '[' && !bracketed) {
                appendPathToken(path, token);
                bracketed = true;
            } else if (character == ']' && bracketed) {
                appendPathToken(path, token);
                bracketed = false;
            } else {
                token.append(character);
            }
        }
        appendPathToken(path, token);
        return List.copyOf(path);
    }

    private static void appendPathToken(List<String> path, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String value = token.toString();
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))) {
            value = value.substring(1, value.length() - 1);
        }
        path.add(value);
        token.setLength(0);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body,
                                                          HttpHeaders headers,
                                                          HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (!ProblemCatalog.supportsHttpStatus(statusCode.value())) {
            return super.createResponseEntity(body, headers, statusCode, request);
        }
        if (body instanceof ProblemDetail problemDetail
                && RequestValidationProblem.TYPE.equals(problemDetail.getType())) {
            return super.createResponseEntity(body, headers, statusCode, request);
        }
        ProblemDescriptor descriptor = ProblemCatalog.forHttpStatus(statusCode.value());
        ProblemDetail problem = ProblemDetailRenderer.render(descriptor);
        if (statusCode.is4xxClientError() && body instanceof ProblemDetail springProblem) {
            copySpringProblemDetails(springProblem, problem);
        }
        return super.createResponseEntity(problem, headers,
                HttpStatusCode.valueOf(descriptor.status()), request);
    }

    private static void copySpringProblemDetails(ProblemDetail source, ProblemDetail target) {
        if (source.getTitle() != null) {
            target.setTitle(source.getTitle());
        }
        if (source.getDetail() != null) {
            target.setDetail(source.getDetail());
        }
        target.setInstance(source.getInstance());
    }

}
