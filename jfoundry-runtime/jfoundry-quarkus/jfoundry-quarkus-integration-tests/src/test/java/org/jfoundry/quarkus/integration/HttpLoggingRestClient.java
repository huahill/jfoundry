package org.jfoundry.quarkus.integration;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/jfoundry/http-logging")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface HttpLoggingRestClient {

    @POST
    Map<String, String> echo(@QueryParam("access_token") String accessToken,
            @HeaderParam("Authorization") String authorization, Map<String, String> request);
}
