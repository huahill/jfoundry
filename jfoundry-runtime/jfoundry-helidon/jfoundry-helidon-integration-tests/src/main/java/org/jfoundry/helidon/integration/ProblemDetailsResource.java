package org.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.InvalidArgumentException;

import java.util.List;

/// Consumer endpoint for the shared JFoundry problem response contract.
@Path("/jfoundry/problems")
@ApplicationScoped
public class ProblemDetailsResource {

    @GET
    public String invalidArgument() {
        throw new InvalidArgumentException("order id is required");
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

    public static final class DeploymentRequest {

        @NotEmpty(message = "must not be empty")
        private List<String> services;

        public List<String> getServices() {
            return services;
        }

        public void setServices(List<String> services) {
            this.services = services;
        }
    }
}
