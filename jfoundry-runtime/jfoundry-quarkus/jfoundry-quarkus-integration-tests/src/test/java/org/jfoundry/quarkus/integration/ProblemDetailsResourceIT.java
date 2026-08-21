package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusIntegrationTest
class ProblemDetailsResourceIT {

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
}
