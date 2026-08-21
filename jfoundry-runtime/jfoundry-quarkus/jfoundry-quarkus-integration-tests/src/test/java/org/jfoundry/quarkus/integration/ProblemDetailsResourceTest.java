package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class ProblemDetailsResourceTest {

    @Test
    void rendersJfoundryExceptionsAsProblemJson() {
        when()
                .get("/jfoundry/problems/invalid-argument")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:jfoundry:problem:invalid-argument"))
                .body("title", equalTo("Invalid argument"))
                .body("status", equalTo(400))
                .body("detail", equalTo("order id is required"))
                .body("$", not(hasKey("code")));
    }

    @Test
    void rendersRoutingMethodNotAllowedResponsesAsProblemJson() {
        when()
                .post("/jfoundry/problems/method-not-allowed")
                .then()
                .statusCode(405)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:jfoundry:problem:http-method-not-allowed"))
                .body("$", not(hasKey("code")));
    }

    @Test
    void preservesAllowForMethodNotAllowedResponsesThatProvideIt() {
        when()
                .get("/jfoundry/problems/provided-allow")
                .then()
                .statusCode(405)
                .contentType("application/problem+json")
                .header("Allow", containsString("GET"))
                .body("type", equalTo("urn:jfoundry:problem:http-method-not-allowed"))
                .body("$", not(hasKey("code")));
    }

    @Test
    void rendersRequestValidationAsTheSharedProblem() {
        given()
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/jfoundry/problems/deployments")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:jfoundry:problem:request-validation"))
                .body("title", equalTo("Request validation failed"))
                .body("status", equalTo(400))
                .body("detail", equalTo("The request failed validation. See 'errors' for details."))
                .body("errors[0].pointer", equalTo("#/services"))
                .body("errors[0].detail", equalTo("must not be empty"))
                .body("errors[0]", not(hasKey("rejectedValue")))
                .body("$", not(hasKey("code")));
    }

    @Test
    void rendersNonDocumentRequestValidationWithoutPointers() {
        given()
                .queryParam("value", "x")
                .when()
                .get("/jfoundry/problems/validation/query")
                .then()
                .statusCode(400)
                .body("type", equalTo("urn:jfoundry:problem:request-validation"))
                .body("errors[0].detail", equalTo("must have at least 3 characters"))
                .body("errors[0]", not(hasKey("pointer")));

        when()
                .get("/jfoundry/problems/validation/path/x")
                .then()
                .statusCode(400)
                .body("type", equalTo("urn:jfoundry:problem:request-validation"))
                .body("errors[0].detail", equalTo("must have at least 3 characters"))
                .body("errors[0]", not(hasKey("pointer")));

        given()
                .header("X-Value", "x")
                .when()
                .get("/jfoundry/problems/validation/header")
                .then()
                .statusCode(400)
                .body("type", equalTo("urn:jfoundry:problem:request-validation"))
                .body("errors[0].detail", equalTo("must have at least 3 characters"))
                .body("errors[0]", not(hasKey("pointer")));
    }
}
