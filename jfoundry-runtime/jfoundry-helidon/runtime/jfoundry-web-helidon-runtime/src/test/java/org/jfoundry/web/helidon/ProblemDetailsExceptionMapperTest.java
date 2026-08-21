package org.jfoundry.web.helidon;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsExceptionMapperTest {

    @Test
    void rendersJfoundryExceptionsAsProblemJson() {
        Response response = new ProblemDetailsExceptionMappers.InvalidArgumentMapper()
                .toResponse(new InvalidArgumentException("order id is required"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getEntity()).isInstanceOf(JsonObject.class);
        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getString("type")).isEqualTo("urn:jfoundry:problem:invalid-argument");
        assertThat(problem.getString("title")).isEqualTo("Invalid argument");
        assertThat(problem.getInt("status")).isEqualTo(400);
        assertThat(problem.getString("detail")).isEqualTo("order id is required");
        assertThat(problem.containsKey("code")).isFalse();
    }

    @Test
    void rendersAReviewedExternalAccessPublicDetail() {
        Response response = new ProblemDetailsExceptionMappers.ExternalAccessMapper().toResponse(
                new ReviewedExternalAccessException("MKS deployment JWT signing failed",
                        new IllegalStateException("private key is invalid"),
                        "Deployment authorization is temporarily unavailable."));

        assertThat(response.getStatus()).isEqualTo(503);
        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getString("type")).isEqualTo("urn:jfoundry:problem:external-access");
        assertThat(problem.getString("title")).isEqualTo("Service temporarily unavailable");
        assertThat(problem.getInt("status")).isEqualTo(503);
        assertThat(problem.getString("detail"))
                .isEqualTo("Deployment authorization is temporarily unavailable.");
        assertThat(problem.containsKey("code")).isFalse();
    }

    @Test
    void retainsAllowHeaderForMethodNotAllowedResponses() {
        Response source = Response.status(405).header(HttpHeaders.ALLOW, "GET, HEAD").build();
        Response response = new ProblemDetailsExceptionMappers.WebApplicationMapper()
                .toResponse(new NotAllowedException(source));

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeaderString(HttpHeaders.ALLOW)).isEqualTo("GET, HEAD");
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
    }

    @Test
    void rendersDescriptorsForSecurityAdapters() {
        Response response = ProblemDetailsRenderer.render(new ProblemDescriptor(
                java.net.URI.create("urn:company:problem:forbidden"), "Forbidden", 403,
                "Access is denied.", java.util.Map.of("policy", "administrators")));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getString("policy")).isEqualTo("administrators");
    }

    @Test
    void preservesJsonExtensionValueTypes() {
        Response response = ProblemDetailsRenderer.render(new ProblemDescriptor(
                java.net.URI.create("urn:company:problem:validation"), "Validation failed", 422,
                "A field is invalid.", java.util.Map.of(
                "attempt", 3,
                "retryable", false,
                "fields", java.util.List.of("name", "amount"),
                "metadata", java.util.Map.of("source", "api"))));

        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getInt("attempt")).isEqualTo(3);
        assertThat(problem.getBoolean("retryable")).isFalse();
        JsonArray fields = problem.getJsonArray("fields");
        assertThat(fields.getString(0)).isEqualTo("name");
        assertThat(problem.getJsonObject("metadata").getString("source")).isEqualTo("api");
    }

    @Test
    void exposesEachMapperAsAJaxRsProvider() {
        assertThat(ProblemDetailsExceptionMappers.InvalidArgumentMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.NotFoundMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.ConflictMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.ExternalAccessMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.DomainRuleViolationMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.DomainStateMapper.class.isAnnotationPresent(Provider.class)).isTrue();
        assertThat(ProblemDetailsExceptionMappers.WebApplicationMapper.class.isAnnotationPresent(Provider.class)).isTrue();
    }

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }
}
