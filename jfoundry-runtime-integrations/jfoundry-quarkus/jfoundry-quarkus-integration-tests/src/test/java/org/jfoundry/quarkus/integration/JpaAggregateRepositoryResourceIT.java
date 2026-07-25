package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
class JpaAggregateRepositoryResourceIT {

    @Test
    void persistsAnAggregateAcrossTheQuarkusTransactionRunnerBoundary() {
        when()
                .get("/jfoundry/jpa")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("PAID"));
    }
}
