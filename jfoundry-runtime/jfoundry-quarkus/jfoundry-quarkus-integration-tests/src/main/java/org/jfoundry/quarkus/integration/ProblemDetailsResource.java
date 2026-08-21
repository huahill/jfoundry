package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.InvalidArgumentException;

import java.util.List;

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

    @POST
    @Path("/deployments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createDeployment(@Valid DeploymentRequest request) {
        return Response.noContent().build();
    }

    @GET
    @Path("/validation/query")
    public Response validateQuery(
            @QueryParam("value") @Size(min = 3, message = "must have at least 3 characters") String value) {
        return Response.noContent().build();
    }

    @GET
    @Path("/validation/path/{value}")
    public Response validatePath(
            @PathParam("value") @Size(min = 3, message = "must have at least 3 characters") String value) {
        return Response.noContent().build();
    }

    @GET
    @Path("/validation/header")
    public Response validateHeader(
            @HeaderParam("X-Value") @Size(min = 3, message = "must have at least 3 characters") String value) {
        return Response.noContent().build();
    }

    public record DeploymentRequest(@NotEmpty(message = "must not be empty") List<String> services) {
    }
}
