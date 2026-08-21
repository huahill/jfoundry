package org.jfoundry.webmvc.spring;

import jakarta.servlet.http.HttpServletResponse;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.jfoundry.web.spring.ProblemDetailRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailsExceptionHandlerTest {

    private final ProblemDetailsExceptionHandler handler = new ProblemDetailsExceptionHandler();

    @Test
    void mapsInvalidArgumentToBadRequestProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleInvalidArgument(
                new InvalidArgumentException("pageSize must not exceed 200"));

        assertProblem(response, HttpStatus.BAD_REQUEST, "Invalid argument",
                "urn:jfoundry:problem:invalid-argument");
    }

    @Test
    void mapsNotFoundToNotFoundProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(new NotFoundException("Environment not found"));

        assertProblem(response, HttpStatus.NOT_FOUND, "Not found",
                "urn:jfoundry:problem:not-found");
    }

    @Test
    void mapsConflictToConflictProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleConflict(new ConflictException("Version conflict"));

        assertProblem(response, HttpStatus.CONFLICT, "Conflict",
                "urn:jfoundry:problem:conflict");
    }

    @Test
    void mapsExternalAccessToServiceUnavailableProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleExternalAccess(
                new ExternalAccessException("k8s api https://cluster.internal timed out"));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable",
                "urn:jfoundry:problem:external-access");
        assertThat(response.getBody().getDetail()).isEqualTo("The requested operation is temporarily unavailable.");
    }

    @Test
    void rendersAReviewedExternalAccessPublicDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleExternalAccess(
                new ReviewedExternalAccessException("MKS deployment JWT signing failed",
                        new IllegalStateException("private key is invalid"),
                        "Deployment authorization is temporarily unavailable."));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable",
                "urn:jfoundry:problem:external-access");
        assertThat(response.getBody().getDetail())
                .isEqualTo("Deployment authorization is temporarily unavailable.");
    }

    @Test
    void mapsDomainRuleViolationToUnprocessableContentProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleDomainRuleViolation(
                new DomainRuleViolationException("Quota exceeded"));

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "Domain rule violation",
                "urn:jfoundry:problem:domain-rule-violation");
    }

    @Test
    void mapsDomainStateToConflictProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleDomainState(
                new DomainStateException("Cannot delete running environment"));

        assertProblem(response, HttpStatus.CONFLICT, "Domain state conflict",
                "urn:jfoundry:problem:domain-state");
    }

    @Test
    void rendersDescriptorsForSecurityAdapters() {
        ProblemDetail problem = ProblemDetailRenderer.render(new ProblemDescriptor(
                java.net.URI.create("urn:company:problem:unauthenticated"), "Unauthenticated", 401,
                "Authentication is required.", Map.of("realm", "deployments")));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getType()).hasToString("urn:company:problem:unauthenticated");
        assertThat(problem.getProperties()).containsEntry("realm", "deployments");
    }

    @Test
    void rendersAnApplicationProblemMapperBeforeJFoundryDefaults() {
        ProblemMapper applicationMapper = exception -> Optional.of(new ProblemDescriptor(
                java.net.URI.create("https://example.test/problems/validation"), "Validation failed", 422,
                "The request violates an application rule.", Map.of("field", "name")));
        ProblemDetailsExceptionHandler applicationHandler = new ProblemDetailsExceptionHandler(applicationMapper);

        ResponseEntity<ProblemDetail> response = applicationHandler.handleInvalidArgument(
                new InvalidArgumentException("internal detail"));

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed",
                "https://example.test/problems/validation");
        assertThat(response.getBody().getProperties()).containsEntry("field", "name");
    }

    @Test
    void mapsUnhandledExceptionsWithAnApplicationProblemMapper() {
        ProblemMapper applicationMapper = exception -> Optional.of(new ProblemDescriptor(
                java.net.URI.create("https://example.test/problems/application"), "Application failure", 422,
                "The application cannot complete the request.", Map.of()));
        ProblemDetailsExceptionHandler applicationHandler = new ProblemDetailsExceptionHandler(applicationMapper);

        ResponseEntity<ProblemDetail> response = applicationHandler.handleUnhandled(
                new IllegalStateException("internal"));

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "Application failure",
                "https://example.test/problems/application");
    }

    @Test
    void mapsUnhandledExceptionsToASafeInternalServerErrorByDefault() {
        ResponseEntity<ProblemDetail> response = handler.handleUnhandled(new IllegalStateException("internal"));

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "urn:jfoundry:problem:internal-error");
        assertThat(response.getBody().getDetail()).isEqualTo("The server failed to process the request.");
    }

    @Test
    void leavesAccessDeniedExceptionsForOuterSecurityFilters() throws Exception {
        AtomicReference<Exception> propagated = new AtomicReference<>();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler)
                .addFilters((request, response, chain) -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (Exception exception) {
                        propagated.set(exception);
                        ((HttpServletResponse) response).setStatus(HttpStatus.FORBIDDEN.value());
                    }
                })
                .build();

        mockMvc.perform(get("/failing"))
                .andExpect(status().isForbidden());

        assertThat(propagated).hasValueSatisfying(exception ->
                assertThat(exception).hasCauseInstanceOf(AccessDeniedException.class));
    }

    @ParameterizedTest
    @MethodSource("httpExceptionCases")
    void mapsSpringMvcExceptionsToSpecificHttpProblemDetails(Exception exception,
                                                             int status,
                                                             String type,
                                                             String title,
                                                             String detail) throws Exception {
        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response).isNotNull();
        assertProblem(response, status, type);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
    }

    @Test
    void preservesSpringProblemDetailsCreatedForUnreadableRequests() throws Exception {
        HttpInputMessage inputMessage = new TestHttpInputMessage();
        var exception = new HttpMessageNotReadableException("JSON parser diagnostic", inputMessage);

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response).isNotNull();
        assertProblem(response, HttpStatus.BAD_REQUEST.value(), "urn:jfoundry:problem:http-bad-request");
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Failed to read request");
        assertThat(problem.toString()).doesNotContain("JSON parser diagnostic");
    }

    @Test
    void preservesLocalizedSpringProblemTitlesAndDetails() throws Exception {
        var localizedHandler = new ProblemDetailsExceptionHandler();
        var messages = new StaticMessageSource();
        Locale locale = Locale.SIMPLIFIED_CHINESE;
        messages.addMessage(ErrorResponse.getDefaultTitleMessageCode(HttpRequestMethodNotSupportedException.class),
                locale, "请求方法不受支持");
        messages.addMessage(ErrorResponse.getDefaultDetailMessageCode(
                HttpRequestMethodNotSupportedException.class, null), locale, "当前资源不支持该请求方法。");
        localizedHandler.setMessageSource(messages);
        LocaleContextHolder.setLocale(locale);

        try {
            ResponseEntity<Object> response = localizedHandler.handleException(
                    new HttpRequestMethodNotSupportedException("POST", List.of("GET")), webRequest());

            assertThat(response).isNotNull();
            assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED.value(),
                    "urn:jfoundry:problem:http-method-not-allowed");
            ProblemDetail problem = (ProblemDetail) response.getBody();
            assertThat(problem.getTitle()).isEqualTo("请求方法不受支持");
            assertThat(problem.getDetail()).isEqualTo("当前资源不支持该请求方法。");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void mapsTypeMismatchWithoutExposingTheRejectedValue() throws Exception {
        var exception = new TypeMismatchException(
                new PropertyChangeEvent(this, "pageSize", null, "secret-value"), Integer.class, null);

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response).isNotNull();
        assertProblem(response, HttpStatus.BAD_REQUEST.value(), "urn:jfoundry:problem:http-bad-request");
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Failed to convert request value for 'pageSize'.");
        assertThat(problem.toString()).doesNotContain("secret-value");
    }

    @Test
    void keepsServerFailureDetailsSafe() throws Exception {
        var exception = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "database password rejected");

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response).isNotNull();
        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                "urn:jfoundry:problem:http-service-unavailable");
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("Service unavailable");
        assertThat(problem.getDetail()).isEqualTo("The service is temporarily unavailable.");
        assertThat(problem.toString()).doesNotContain("database password rejected");
    }

    @Test
    void mapsRequestBodyValidationErrorsToFieldLevelProblemDetails() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new ValidationRequest(null), "request");
        bindingResult.addError(new FieldError("request", "services", "secret-services", false,
                new String[]{"NotEmpty"}, null, "must not be empty"));
        bindingResult.addError(new FieldError("request", "services[0].image", "secret-value", false,
                new String[]{"URL"}, null, "must be a valid URL"));
        bindingResult.addError(new FieldError("request", "metadata[a/b~c]", "another-secret", false,
                new String[]{"Valid"}, null, "is invalid"));
        MethodParameter parameter = new MethodParameter(
                ValidationController.class.getDeclaredMethod("validate", ValidationRequest.class), 0);
        var exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertProblem(response, HttpStatus.BAD_REQUEST.value(), "urn:jfoundry:problem:request-validation");
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("Request validation failed");
        assertThat(problem.getDetail())
                .isEqualTo("The request failed validation. See 'errors' for details.");
        assertThat(problem.getProperties()).containsEntry("errors",
                List.of(
                        Map.of("detail", "must not be empty", "pointer", "#/services"),
                        Map.of("detail", "must be a valid URL", "pointer", "#/services/0/image"),
                        Map.of("detail", "is invalid", "pointer", "#/metadata/a~1b~0c")));
        assertThat(problem.toString()).doesNotContain("secret-services", "secret-value", "another-secret");
    }

    @Test
    void mapsObjectValidationErrorsWithoutExposingRejectedValues() throws Exception {
        var rejectedRequest = new ValidationRequest(List.of("secret-value"));
        var bindingResult = new BeanPropertyBindingResult(rejectedRequest, "request");
        bindingResult.addError(new ObjectError("request", null));
        MethodParameter parameter = new MethodParameter(
                ValidationController.class.getDeclaredMethod("validate", ValidationRequest.class), 0);
        var exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getDetail())
                .isEqualTo("The request failed validation. See 'errors' for details.");
        assertThat(problem.getProperties()).containsEntry("errors",
                List.of(Map.of("detail", "is invalid")));
        assertThat(problem.toString()).doesNotContain("secret-value");
    }

    @Test
    void preservesUnsupportedSpringMvcProblemDetails() throws Exception {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests");

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getDetail()).isEqualTo("Too many requests");
        assertThat(problem.getProperties()).isNull();
    }

    private static Stream<Arguments> httpExceptionCases() {
        return Stream.of(
                Arguments.of(new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                        405, "urn:jfoundry:problem:http-method-not-allowed", "Method Not Allowed",
                        "Method 'POST' is not supported."),
                Arguments.of(new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML,
                        List.of(MediaType.APPLICATION_JSON)), 415,
                        "urn:jfoundry:problem:http-unsupported-media-type", "Unsupported Media Type",
                        "Content-Type 'application/xml' is not supported."),
                Arguments.of(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                        406, "urn:jfoundry:problem:http-not-acceptable", "Not Acceptable",
                        "Acceptable representations: [application/json]."),
                Arguments.of(new MissingServletRequestParameterException("environmentId", "String"),
                        400, "urn:jfoundry:problem:http-bad-request", "Bad Request",
                        "Required parameter 'environmentId' is not present.")
        );
    }

    private static WebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }

    @RestController
    private static final class FailingController {

        @GetMapping("/failing")
        void fail() {
            throw new AccessDeniedException("forbidden");
        }
    }

    private static final class ValidationController {

        void validate(ValidationRequest request) {
        }
    }

    private record ValidationRequest(List<String> services) {
    }

    private static final class TestHttpInputMessage implements HttpInputMessage {

        @Override
        public java.io.InputStream getBody() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return org.springframework.http.HttpHeaders.EMPTY;
        }
    }

    private static void assertProblem(ResponseEntity<ProblemDetail> response,
                                      HttpStatus status,
                                      String title,
                                      String type) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getTitle()).isEqualTo(title);
        assertThat(response.getBody().getType()).hasToString(type);
        assertThat(response.getBody().getProperties() == null ? Map.of() : response.getBody().getProperties())
                .doesNotContainKey("code");
    }

    private static void assertProblem(ResponseEntity<Object> response, int status, String type) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getType()).hasToString(type);
        assertThat(problem.getProperties() == null ? Map.of() : problem.getProperties()).doesNotContainKey("code");
    }
}
