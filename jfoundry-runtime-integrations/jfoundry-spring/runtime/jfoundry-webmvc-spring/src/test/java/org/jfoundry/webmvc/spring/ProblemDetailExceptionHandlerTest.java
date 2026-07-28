package org.jfoundry.webmvc.spring;

import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailExceptionHandlerTest {

    private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler();

    @Test
    void mapsInvalidArgumentToBadRequestProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleInvalidArgument(
                new InvalidArgumentException("pageSize must not exceed 200"));

        assertProblem(response, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Invalid argument",
                "urn:jfoundry:problem:invalid-argument");
    }

    @Test
    void mapsNotFoundToNotFoundProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(new NotFoundException("Environment not found"));

        assertProblem(response, HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found",
                "urn:jfoundry:problem:not-found");
    }

    @Test
    void mapsConflictToConflictProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleConflict(new ConflictException("Version conflict"));

        assertProblem(response, HttpStatus.CONFLICT, "CONFLICT", "Conflict",
                "urn:jfoundry:problem:conflict");
    }

    @Test
    void mapsExternalAccessToServiceUnavailableProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleExternalAccess(
                new ExternalAccessException("k8s api https://cluster.internal timed out"));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_ACCESS", "Service temporarily unavailable",
                "urn:jfoundry:problem:external-access");
        assertThat(response.getBody().getDetail()).isEqualTo("The requested operation is temporarily unavailable.");
    }

    @Test
    void mapsDomainRuleViolationToUnprocessableEntityProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleDomainRuleViolation(
                new DomainRuleViolationException("Quota exceeded"));

        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_RULE_VIOLATION", "Domain rule violation",
                "urn:jfoundry:problem:domain-rule-violation");
    }

    @Test
    void mapsDomainStateToConflictProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleDomainState(
                new DomainStateException("Cannot delete running environment"));

        assertProblem(response, HttpStatus.CONFLICT, "DOMAIN_STATE", "Domain state conflict",
                "urn:jfoundry:problem:domain-state");
    }

    @Test
    void rendersAnApplicationProblemMapperBeforeJFoundryDefaults() {
        ProblemMapper applicationMapper = exception -> Optional.of(new ProblemDescriptor(
                java.net.URI.create("https://example.test/problems/validation"), "Validation failed", 422,
                "The request violates an application rule.", Map.of("code", "APPLICATION_VALIDATION", "field", "name")));
        ProblemDetailExceptionHandler applicationHandler = new ProblemDetailExceptionHandler(applicationMapper);

        ResponseEntity<ProblemDetail> response = applicationHandler.handleInvalidArgument(
                new InvalidArgumentException("internal detail"));

        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "APPLICATION_VALIDATION", "Validation failed",
                "https://example.test/problems/validation");
        assertThat(response.getBody().getProperties()).containsEntry("field", "name");
    }

    @ParameterizedTest
    @MethodSource("httpExceptionCases")
    void mapsSpringMvcExceptionsToHttpProblemCodes(Exception exception, int status, String code) throws Exception {
        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response).isNotNull();
        assertProblem(response, status, code);
    }

    private static Stream<Arguments> httpExceptionCases() {
        return Stream.of(
                Arguments.of(new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                        405, "HTTP_METHOD_NOT_ALLOWED"),
                Arguments.of(new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML,
                        List.of(MediaType.APPLICATION_JSON)), 415, "HTTP_UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                        406, "HTTP_NOT_ACCEPTABLE")
        );
    }

    private static WebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }

    private static void assertProblem(ResponseEntity<ProblemDetail> response,
                                      HttpStatus status,
                                      String code,
                                      String title,
                                      String type) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getTitle()).isEqualTo(title);
        assertThat(response.getBody().getType()).hasToString(type);
        assertThat(response.getBody().getProperties()).containsEntry("code", code);
    }

    private static void assertProblem(ResponseEntity<Object> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getProperties()).containsEntry("code", code);
    }
}
