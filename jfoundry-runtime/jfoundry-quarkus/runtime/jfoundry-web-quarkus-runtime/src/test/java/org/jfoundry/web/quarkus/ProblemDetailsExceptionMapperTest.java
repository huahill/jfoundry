package org.jfoundry.web.quarkus;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemDetailsExceptionMapperTest {

    private final ProblemDetailsExceptionMappers.InvalidArgumentMapper invalidArgumentMapper = new ProblemDetailsExceptionMappers.InvalidArgumentMapper();
    private final ProblemDetailsExceptionMappers.WebApplicationMapper webApplicationMapper = new ProblemDetailsExceptionMappers.WebApplicationMapper();

    @Test
    void rendersJfoundryExceptionsAsProblemJson() {
        Response response = invalidArgumentMapper.toResponse(new InvalidArgumentException("order id is required"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getEntity()).isEqualTo(Map.of(
                "type", "urn:jfoundry:problem:invalid-argument",
                "title", "Invalid argument",
                "status", 400,
                "detail", "order id is required"));
    }

    @Test
    void rendersAReviewedExternalAccessPublicDetail() {
        Response response = new ProblemDetailsExceptionMappers.ExternalAccessMapper().toResponse(
                new ReviewedExternalAccessException("MKS deployment JWT signing failed",
                        new IllegalStateException("private key is invalid"),
                        "Deployment authorization is temporarily unavailable."));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getEntity()).isEqualTo(Map.of(
                "type", "urn:jfoundry:problem:external-access",
                "title", "Service temporarily unavailable",
                "status", 503,
                "detail", "Deployment authorization is temporarily unavailable."));
    }

    @Test
    void retainsAllowHeaderForMethodNotAllowedResponses() {
        Response source = Response.status(405).header(HttpHeaders.ALLOW, "GET, HEAD").build();
        Response response = webApplicationMapper.toResponse(new NotAllowedException(source));

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeaderString(HttpHeaders.ALLOW)).isEqualTo("GET, HEAD");
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
    }

    @Test
    void rendersDescriptorsForSecurityAdapters() {
        Response response = ProblemDetailsRenderer.render(new ProblemDescriptor(
                java.net.URI.create("urn:company:problem:forbidden"), "Forbidden", 403,
                "Access is denied.", Map.of("policy", "administrators")));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getEntity()).isEqualTo(Map.of(
                "type", "urn:company:problem:forbidden",
                "title", "Forbidden",
                "status", 403,
                "detail", "Access is denied.",
                "policy", "administrators"));
    }

    @Test
    void rendersRestRequestValidationAsTheSharedProblem() throws Exception {
        Set<? extends jakarta.validation.ConstraintViolation<?>> violations = requestViolations(
                new ValidationRequest(List.of()));
        Response response = new ProblemDetailsExceptionMappers.RequestValidationMapper()
                .toResponse(new ResteasyReactiveViolationException(violations));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getEntity()).isEqualTo(Map.of(
                "type", "urn:jfoundry:problem:request-validation",
                "title", "Request validation failed",
                "status", 400,
                "detail", "The request failed validation. See 'errors' for details.",
                "errors", List.of(Map.of(
                        "pointer", "#/services",
                        "detail", "must not be empty"))));
    }

    @Test
    void doesNotExposeRestReturnValueValidationAsAClientError() throws Exception {
        Set<? extends jakarta.validation.ConstraintViolation<?>> violations = returnValueViolations();
        ResteasyReactiveViolationException exception = new ResteasyReactiveViolationException(violations);

        assertThatThrownBy(() -> new ProblemDetailsExceptionMappers.RequestValidationMapper().toResponse(exception))
                .isSameAs(exception);
    }

    private static Set<? extends jakarta.validation.ConstraintViolation<?>> requestViolations(
            ValidationRequest request) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ValidationResource resource = new ValidationResource();
            return validator.forExecutables().validateParameters(
                    resource,
                    ValidationResource.class.getDeclaredMethod("create", ValidationRequest.class),
                    new Object[]{request});
        }
    }

    private static Set<? extends jakarta.validation.ConstraintViolation<?>> returnValueViolations() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ValidationResource resource = new ValidationResource();
            return validator.forExecutables().validateReturnValue(
                    resource,
                    ValidationResource.class.getDeclaredMethod("result"),
                    "");
        }
    }

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }

    private record ValidationRequest(@NotEmpty(message = "must not be empty") List<String> services) {
    }

    @Path("/validation")
    private static final class ValidationResource {

        public void create(@Valid ValidationRequest request) {
        }

        @NotEmpty(message = "must not be empty")
        public String result() {
            return "";
        }
    }
}
