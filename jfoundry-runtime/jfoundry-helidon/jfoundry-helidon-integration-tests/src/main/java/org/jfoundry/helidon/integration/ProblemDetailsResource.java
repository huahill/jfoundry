package org.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jfoundry.application.exception.InvalidArgumentException;

/// Consumer endpoint for the shared JFoundry problem response contract.
@Path("/jfoundry/problems")
@ApplicationScoped
public class ProblemDetailsResource {

    @GET
    public String invalidArgument() {
        throw new InvalidArgumentException("order id is required");
    }
}
