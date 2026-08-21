package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
@TestProfile(PostgreSqlIntegrationTestProfile.class)
class DomainEventExternalizationResourceIT {

    @Test
    void recordsAnExternalizedDomainEventInTheTransactionalOutbox() {
        when()
                .get("/jfoundry/domain-event-externalization")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("orders.created:order-1:PENDING:true:true"));
    }
}
