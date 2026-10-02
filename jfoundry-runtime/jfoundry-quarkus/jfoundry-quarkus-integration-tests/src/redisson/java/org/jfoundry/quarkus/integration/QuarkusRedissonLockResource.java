package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;

import java.time.Duration;

/// HTTP operation that verifies the JFoundry lock abstraction against a real Redis server.
@Path("/jfoundry/native/redisson/lock")
@ApplicationScoped
public class QuarkusRedissonLockResource {

    private final LockExecutor lockExecutor;
    private final QuarkusAnnotatedRedissonLock annotatedLock;

    public QuarkusRedissonLockResource(LockExecutor lockExecutor, QuarkusAnnotatedRedissonLock annotatedLock) {
        this.lockExecutor = lockExecutor;
        this.annotatedLock = annotatedLock;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String acquireAndReleaseLock() throws Exception {
        boolean locked = lockExecutor.execute(
                new LockKey("native-redisson", "verification"),
                LockOptions.builder().leaseTime(Duration.ofSeconds(5)).build(),
                () -> true);
        return "{\"locked\":" + locked + "}";
    }

    @GET
    @Path("annotated")
    @Produces(MediaType.APPLICATION_JSON)
    public String acquireAnnotatedLock(@QueryParam("orderId") String orderId) {
        return "{\"locked\":" + annotatedLock.acquire(orderId) + "}";
    }
}
