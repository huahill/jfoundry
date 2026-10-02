package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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

    public QuarkusRedissonLockResource(LockExecutor lockExecutor) {
        this.lockExecutor = lockExecutor;
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
}
