package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.InvalidArgumentException;

@Path("/jfoundry/problems")
@ApplicationScoped
public class ProblemDetailsResource {

    @GET
    @Path("/invalid-argument")
    @Produces(MediaType.TEXT_PLAIN)
    public String invalidArgument() {
        throw new InvalidArgumentException("order id is required");
    }

    @GET
    @Path("/method-not-allowed")
    @Produces(MediaType.TEXT_PLAIN)
    public String methodNotAllowed() {
        return "allowed";
    }

    @GET
    @Path("/provided-allow")
    @Produces(MediaType.TEXT_PLAIN)
    public String providedAllow() {
        throw new NotAllowedException(Response.status(Response.Status.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "GET, HEAD")
                .build());
    }
}
