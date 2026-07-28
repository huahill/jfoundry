package org.jfoundry.web.quarkus;

import jakarta.ws.rs.core.Response;
import org.jfoundry.problem.ProblemDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Renders runtime-neutral problem descriptors as Quarkus REST problem responses.
public final class ProblemDetailsRenderer {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ProblemDetailsRenderer() {
    }

    public static Response render(ProblemDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", descriptor.type().toString());
        problem.put("title", descriptor.title());
        problem.put("status", descriptor.status());
        problem.put("detail", descriptor.detail());
        problem.putAll(descriptor.extensions());
        return Response.status(descriptor.status()).type(PROBLEM_JSON).entity(Map.copyOf(problem)).build();
    }
}
