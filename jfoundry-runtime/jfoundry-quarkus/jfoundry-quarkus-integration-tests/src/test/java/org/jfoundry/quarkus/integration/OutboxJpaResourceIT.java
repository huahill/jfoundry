package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
class OutboxJpaResourceIT {

    @Test
    void persistsAnOutboxMessageAcrossTheQuarkusTransactionRunnerBoundary() {
        when()
                .get("/jfoundry/outbox")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("PENDING"));
    }
}
