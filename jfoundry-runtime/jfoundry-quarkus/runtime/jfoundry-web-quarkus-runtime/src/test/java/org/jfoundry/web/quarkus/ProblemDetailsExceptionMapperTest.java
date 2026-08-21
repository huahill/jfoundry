package org.jfoundry.web.quarkus;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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

    @ParameterizedTest
    @MethodSource("nonDocumentRequestMethods")
    void omitsPointersForNonDocumentRequestParameters(String methodName) throws Exception {
        Set<? extends jakarta.validation.ConstraintViolation<?>> violations = requestViolations(
                methodName, new ValidationRequest(List.of()));

        Response response = new ProblemDetailsExceptionMappers.RequestValidationMapper()
                .toResponse(new ResteasyReactiveViolationException(violations));

        Map<?, ?> problem = (Map<?, ?>) response.getEntity();
        assertThat(problem.get("errors")).isEqualTo(List.of(Map.of("detail", "must not be empty")));
    }

    @Test
    void rendersBodyContainerElementPathsAsPointers() throws Exception {
        Set<? extends ConstraintViolation<?>> violations = requestViolations(
                "bodyContainer", ContainerRequest.class, new ContainerRequest(List.of("")));

        Response response = new ProblemDetailsExceptionMappers.RequestValidationMapper()
                .toResponse(new ResteasyReactiveViolationException(violations));

        Map<?, ?> problem = (Map<?, ?>) response.getEntity();
        assertThat(problem.get("errors")).isEqualTo(List.of(Map.of(
                "pointer", "#/services/0",
                "detail", "must not be empty")));
    }

    @Test
    void rendersClassAndCrossParameterConstraintsWithoutPointers() throws Exception {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.addAll(requestViolations(
                "classLevel", ClassLevelRequest.class, new ClassLevelRequest("left", "right")));
        violations.addAll(crossParameterViolations());

        Response response = new ProblemDetailsExceptionMappers.RequestValidationMapper()
                .toResponse(new ResteasyReactiveViolationException(violations));

        Map<?, ?> problem = (Map<?, ?>) response.getEntity();
        assertThat(problem.get("errors")).isEqualTo(List.of(
                Map.of("detail", "fields are inconsistent"),
                Map.of("detail", "parameters are inconsistent")));
    }

    @Test
    void doesNotPartiallyExposeMixedRequestAndReturnValueValidation() throws Exception {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.addAll(requestViolations(new ValidationRequest(List.of())));
        violations.addAll(returnValueViolations());
        ResteasyReactiveViolationException exception = new ResteasyReactiveViolationException(violations);

        assertThatThrownBy(() -> new ProblemDetailsExceptionMappers.RequestValidationMapper().toResponse(exception))
                .isSameAs(exception);
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
        return requestViolations("create", request);
    }

    private static Set<? extends jakarta.validation.ConstraintViolation<?>> requestViolations(
            String methodName, ValidationRequest request) throws Exception {
        return requestViolations(methodName, ValidationRequest.class, request);
    }

    private static Set<? extends ConstraintViolation<?>> requestViolations(
            String methodName, Class<?> parameterType, Object request) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ValidationResource resource = new ValidationResource();
            return validator.forExecutables().validateParameters(
                    resource,
                    ValidationResource.class.getDeclaredMethod(methodName, parameterType),
                    new Object[]{request});
        }
    }

    private static Set<? extends ConstraintViolation<?>> crossParameterViolations() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ValidationResource resource = new ValidationResource();
            return validator.forExecutables().validateParameters(
                    resource,
                    ValidationResource.class.getDeclaredMethod("crossParameter", String.class, String.class),
                    new Object[]{"left", "right"});
        }
    }

    private static Stream<String> nonDocumentRequestMethods() {
        return Stream.of("query", "path", "header", "cookie", "matrix", "form", "bean", "quarkusQuery");
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

    private record ContainerRequest(List<@NotEmpty(message = "must not be empty") String> services) {
    }

    @ConsistentRequest
    private record ClassLevelRequest(String first, String second) {
    }

    @Path("/validation")
    private static final class ValidationResource {

        public void create(@Valid ValidationRequest request) {
        }

        public void query(@jakarta.ws.rs.QueryParam("filter") @Valid ValidationRequest request) {
        }

        public void path(@jakarta.ws.rs.PathParam("filter") @Valid ValidationRequest request) {
        }

        public void header(@jakarta.ws.rs.HeaderParam("filter") @Valid ValidationRequest request) {
        }

        public void cookie(@jakarta.ws.rs.CookieParam("filter") @Valid ValidationRequest request) {
        }

        public void matrix(@jakarta.ws.rs.MatrixParam("filter") @Valid ValidationRequest request) {
        }

        public void form(@jakarta.ws.rs.FormParam("filter") @Valid ValidationRequest request) {
        }

        public void bean(@jakarta.ws.rs.BeanParam @Valid ValidationRequest request) {
        }

        public void quarkusQuery(@org.jboss.resteasy.reactive.RestQuery @Valid ValidationRequest request) {
        }

        public void bodyContainer(@Valid ContainerRequest request) {
        }

        public void classLevel(@Valid ClassLevelRequest request) {
        }

        @ConsistentParameters
        public void crossParameter(String first, String second) {
        }

        @NotEmpty(message = "must not be empty")
        public String result() {
            return "";
        }
    }

    @Documented
    @Constraint(validatedBy = ConsistentRequestValidator.class)
    @Target({TYPE, ANNOTATION_TYPE})
    @Retention(RUNTIME)
    private @interface ConsistentRequest {

        String message() default "fields are inconsistent";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class ConsistentRequestValidator
            implements ConstraintValidator<ConsistentRequest, ClassLevelRequest> {

        @Override
        public boolean isValid(ClassLevelRequest value, ConstraintValidatorContext context) {
            return value == null || value.first().equals(value.second());
        }
    }

    @Documented
    @Constraint(validatedBy = ConsistentParametersValidator.class)
    @Target({METHOD, CONSTRUCTOR, ANNOTATION_TYPE})
    @Retention(RUNTIME)
    private @interface ConsistentParameters {

        String message() default "parameters are inconsistent";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static final class ConsistentParametersValidator
            implements ConstraintValidator<ConsistentParameters, Object[]> {

        @Override
        public boolean isValid(Object[] value, ConstraintValidatorContext context) {
            return value == null || value.length < 2 || java.util.Objects.equals(value[0], value[1]);
        }
    }
}
