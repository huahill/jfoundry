package org.jfoundry.helidon.integration;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@HelidonTest
class ProblemDetailsResourceTest {

    @Inject
    @Socket("@default")
    WebTarget target;

    @Test
    void doesNotConstrainTheExceptionResponseMediaType() throws NoSuchMethodException {
        Produces produces = ProblemDetailsResource.class.getMethod("invalidArgument").getAnnotation(Produces.class);

        assertNull(produces);
    }

    @Test
    void rendersRequestValidationAsTheSharedProblem() {
        try (Response response = target.path("/jfoundry/problems/deployments")
                .request()
                .post(Entity.json("{}"))) {
            assertEquals(400, response.getStatus());
            assertEquals("application/problem+json", response.getMediaType().toString());
            JsonObject problem = response.readEntity(JsonObject.class);
            assertEquals("urn:jfoundry:problem:request-validation", problem.getString("type"));
            assertEquals("Request validation failed", problem.getString("title"));
            assertEquals(400, problem.getInt("status"));
            assertEquals("The request failed validation. See 'errors' for details.",
                    problem.getString("detail"));
            JsonObject error = problem.getJsonArray("errors").getJsonObject(0);
            assertEquals("#/services", error.getString("pointer"));
            assertEquals("must not be empty", error.getString("detail"));
            assertFalse(error.containsKey("rejectedValue"));
            assertFalse(problem.containsKey("code"));
        }
    }

    @Test
    void rendersNonDocumentRequestValidationWithoutPointers() {
        assertDetailOnlyValidation(target.path("/jfoundry/problems/validation/query")
                .queryParam("value", "x")
                .request()
                .get());
        assertDetailOnlyValidation(target.path("/jfoundry/problems/validation/path/x")
                .request()
                .get());
        assertDetailOnlyValidation(target.path("/jfoundry/problems/validation/header")
                .request()
                .header("X-Value", "x")
                .get());
    }

    private static void assertDetailOnlyValidation(Response response) {
        try (response) {
            assertEquals(400, response.getStatus());
            assertEquals("application/problem+json", response.getMediaType().toString());
            JsonObject problem = response.readEntity(JsonObject.class);
            assertEquals("urn:jfoundry:problem:request-validation", problem.getString("type"));
            JsonObject error = problem.getJsonArray("errors").getJsonObject(0);
            assertEquals("must have at least 3 characters", error.getString("detail"));
            assertFalse(error.containsKey("pointer"));
        }
    }
}
