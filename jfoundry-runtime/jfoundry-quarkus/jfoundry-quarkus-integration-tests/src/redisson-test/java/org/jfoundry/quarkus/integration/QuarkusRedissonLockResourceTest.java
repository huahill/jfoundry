package org.jfoundry.quarkus.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = RedisTestResource.class, restrictToAnnotatedClass = true)
class QuarkusRedissonLockResourceTest {

    @Test
    void acquiresAndReleasesARedissonLock() {
        when()
                .get("/jfoundry/native/redisson/lock")
                .then()
                .statusCode(200)
                .body(equalTo("{\"locked\":true}"));
    }

    @Test
    void acquiresAnAnnotatedRedissonLock() {
        when()
                .get("/jfoundry/native/redisson/lock/annotated?orderId=42")
                .then()
                .statusCode(200)
                .body(equalTo("{\"locked\":true}"));
    }
}
