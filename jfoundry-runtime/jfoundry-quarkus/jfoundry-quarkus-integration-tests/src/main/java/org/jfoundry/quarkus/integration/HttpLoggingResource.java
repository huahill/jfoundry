package org.jfoundry.quarkus.integration;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/jfoundry/http-logging")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HttpLoggingResource {

    @POST
    public Map<String, String> echo(Map<String, String> request) {
        return Map.of("name", request.get("name"), "password", "response-secret");
    }
}
