package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

@QuarkusIntegrationTest
class InboxJpaResourceIT {

    @Test
    void processesTheFirstDeliveryAndSkipsTheDuplicate() {
        when().get("/jfoundry/inbox").then().statusCode(200).body(org.hamcrest.Matchers.equalTo("true,false"));
    }

    @Test
    void retriesAFailedDelivery() {
        when().get("/jfoundry/inbox/retry").then().statusCode(200).body(org.hamcrest.Matchers.equalTo("true"));
    }
}
