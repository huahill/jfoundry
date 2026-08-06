package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
class OutboxMaintenanceResourceIT {

    @Test
    void recoversAStuckDispatchingOutboxMessage() {
        when().get("/jfoundry/outbox/maintenance/recover")
                .then().statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("PENDING:1"));
    }

    @Test
    void removesExpiredPublishedAndDeadLetteredOutboxMessages() {
        when().get("/jfoundry/outbox/maintenance/cleanup")
                .then().statusCode(200)
                .body(org.hamcrest.Matchers.matchesPattern("(?:[2-9]|[1-9][0-9]+):0"));
    }
}
