package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(QuarkusJobRunrDispatchProfile.class)
class QuarkusJobRunrOutboxResourceTest {

    @Test
    void jobRunrBackgroundServerPublishesTheSeededOutboxRow() throws Exception {
        assertThat(awaitDispatch()).contains(
                "\"registered\":true",
                "\"dispatched\":true",
                "\"published\":true");
    }

    private static String awaitDispatch() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        String latest = "";
        while (Instant.now().isBefore(deadline)) {
            latest = when().get("/jfoundry/native/jobrunr/dispatch").then().statusCode(200).extract().asString();
            if (latest.contains("\"dispatched\":true") && latest.contains("\"published\":true")) {
                return latest;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("JobRunr did not publish the Outbox row: " + latest);
    }
}
