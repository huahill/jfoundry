package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
@TestProfile(PostgreSqlIntegrationTestProfile.class)
class DomainEventDispatchResourceIT {

    @Test
    void dispatchesNestedApplicationServiceEventsAtTheOuterBoundary() {
        when()
                .get("/jfoundry/domain-events")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("[outer, inner]"));
    }

    @Test
    void discardsEventsWhenAnApplicationServiceThrows() {
        when()
                .get("/jfoundry/domain-events/failure")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("[]"));
    }

    @Test
    void publishesCdiEventsAfterTheTransactionCommits() {
        when()
                .get("/jfoundry/domain-events/cdi/transaction")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("[outer, inner]"));
    }
}
