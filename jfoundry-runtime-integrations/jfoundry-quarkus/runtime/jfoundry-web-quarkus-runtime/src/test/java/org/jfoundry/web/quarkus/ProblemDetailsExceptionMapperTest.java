package org.jfoundry.web.quarkus;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsExceptionMapperTest {

    private final ProblemDetailsExceptionMappers.InvalidArgumentMapper invalidArgumentMapper = new ProblemDetailsExceptionMappers.InvalidArgumentMapper();
    private final ProblemDetailsExceptionMappers.WebApplicationMapper webApplicationMapper = new ProblemDetailsExceptionMappers.WebApplicationMapper();

    @Test
    void rendersJfoundryExceptionsAsProblemJson() {
        Response response = invalidArgumentMapper.toResponse(new InvalidArgumentException("order id is required"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
        assertThat(response.getEntity()).isEqualTo(Map.of(
                "type", "urn:jfoundry:problem:invalid-argument",
                "title", "Invalid argument",
                "status", 400,
                "detail", "order id is required",
                "code", "INVALID_ARGUMENT"));
    }

    @Test
    void retainsAllowHeaderForMethodNotAllowedResponses() {
        Response source = Response.status(405).header(HttpHeaders.ALLOW, "GET, HEAD").build();
        Response response = webApplicationMapper.toResponse(new NotAllowedException(source));

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeaderString(HttpHeaders.ALLOW)).isEqualTo("GET, HEAD");
        assertThat(response.getMediaType().toString()).isEqualTo("application/problem+json");
    }
}
