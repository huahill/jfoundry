package org.jfoundry.web.helidon;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;
import org.jfoundry.problem.ProblemDescriptor;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/// Renders runtime-neutral problem descriptors as Helidon JAX-RS problem responses.
public final class ProblemDetailsRenderer {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ProblemDetailsRenderer() {
    }

    public static Response render(ProblemDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return Response.status(descriptor.status()).type(PROBLEM_JSON).entity(problemJson(descriptor)).build();
    }

    static JsonObject problemJson(ProblemDescriptor descriptor) {
        var builder = Json.createObjectBuilder()
                .add("type", descriptor.type().toString())
                .add("title", descriptor.title())
                .add("status", descriptor.status())
                .add("detail", descriptor.detail());
        descriptor.extensions().forEach((name, value) -> builder.add(name, jsonValue(value)));
        return builder.build();
    }

    private static JsonValue jsonValue(Object value) {
        if (value == null) {
            return JsonValue.NULL;
        }
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            return Json.createValue(value.toString());
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (value instanceof BigDecimal decimal) {
            return Json.createValue(decimal);
        }
        if (value instanceof BigInteger integer) {
            return Json.createValue(new BigDecimal(integer));
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return Json.createValue(((Number) value).longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            return Json.createValue(((Number) value).doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            map.forEach((name, nestedValue) -> builder.add(String.valueOf(name), jsonValue(nestedValue)));
            return builder.build();
        }
        if (value instanceof Iterable<?> values) {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            values.forEach(nestedValue -> builder.add(jsonValue(nestedValue)));
            return builder.build();
        }
        if (value.getClass().isArray()) {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (int index = 0; index < Array.getLength(value); index++) {
                builder.add(jsonValue(Array.get(value, index)));
            }
            return builder.build();
        }
        return Json.createValue(value.toString());
    }
}
