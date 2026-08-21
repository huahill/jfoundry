package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
@TestProfile(PostgreSqlIntegrationTestProfile.class)
class TransactionRunnerResourceIT {

    @Test
    void executesAnApplicationTransactionThroughTheQuarkusExtension() {
        when()
                .get("/jfoundry/transaction")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("committed"));
    }
}
