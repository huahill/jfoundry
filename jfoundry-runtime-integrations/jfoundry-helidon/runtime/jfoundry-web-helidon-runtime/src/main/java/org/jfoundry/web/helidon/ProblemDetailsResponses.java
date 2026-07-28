package org.jfoundry.web.helidon;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.jfoundry.problem.CompositeProblemMapper;
import org.jfoundry.problem.ProblemCatalog;
import org.jfoundry.problem.ProblemDescriptor;

final class ProblemDetailsResponses {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final CompositeProblemMapper PROBLEM_MAPPER = new CompositeProblemMapper(java.util.List.of());

    private ProblemDetailsResponses() {
    }

    static Response forException(Exception exception) {
        return problem(PROBLEM_MAPPER.map(exception).orElseThrow(), null);
    }

    static Response forHttpStatus(int status, MultivaluedMap<String, Object> headers) {
        return problem(ProblemCatalog.forHttpStatus(status), headers);
    }

    private static Response problem(ProblemDescriptor descriptor, MultivaluedMap<String, Object> headers) {
        Response.ResponseBuilder response = Response.status(descriptor.status())
                .type(PROBLEM_JSON)
                .entity(problemJson(descriptor));
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                    values.forEach(value -> response.header(name, value));
                }
            });
        }
        return response.build();
    }

    private static JsonObject problemJson(ProblemDescriptor descriptor) {
        var builder = Json.createObjectBuilder()
                .add("type", descriptor.type().toString())
                .add("title", descriptor.title())
                .add("status", descriptor.status())
                .add("detail", descriptor.detail());
        descriptor.extensions().forEach((name, value) -> builder.add(name, String.valueOf(value)));
        return builder.build();
    }
}
