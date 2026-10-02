package org.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/// HTTP operation that verifies annotated Helidon lock interception against Redis.
@Path("/jfoundry/helidon/redisson/lock")
@ApplicationScoped
public class HelidonRedissonLockResource {

    private final HelidonAnnotatedRedissonLock annotatedLock;

    @Inject
    public HelidonRedissonLockResource(HelidonAnnotatedRedissonLock annotatedLock) {
        this.annotatedLock = annotatedLock;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String acquire(@QueryParam("orderId") String orderId) {
        return "{\"locked\":" + annotatedLock.acquire(orderId) + "}";
    }
}
