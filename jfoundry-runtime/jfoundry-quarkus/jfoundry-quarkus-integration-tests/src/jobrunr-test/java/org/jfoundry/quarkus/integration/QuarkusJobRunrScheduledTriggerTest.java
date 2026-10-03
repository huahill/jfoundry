package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(QuarkusJobRunrIdleProfile.class)
class QuarkusJobRunrScheduledTriggerTest {

    @Test
    void scheduledTriggerDoesNotDispatchWhileJobRunrOwnsTheMode() throws Exception {
        String registered = awaitRegistered();
        assertThat(registered).contains("\"published\":false", "\"dispatched\":false");

        Thread.sleep(Duration.ofSeconds(8).toMillis());

        String afterSchedulerWindow = dispatchResult();
        assertThat(afterSchedulerWindow).contains(
                "\"registered\":true",
                "\"dispatched\":false",
                "\"published\":false");
    }

    private static String awaitRegistered() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        String latest = "";
        while (Instant.now().isBefore(deadline)) {
            latest = dispatchResult();
            assertThat(latest).doesNotContain("\"published\":true", "\"dispatched\":true");
            if (latest.contains("\"registered\":true")) {
                return latest;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("JobRunr did not register the Outbox recurring job: " + latest);
    }

    private static String dispatchResult() {
        return when().get("/jfoundry/native/jobrunr/dispatch").then().statusCode(200).extract().asString();
    }
}
