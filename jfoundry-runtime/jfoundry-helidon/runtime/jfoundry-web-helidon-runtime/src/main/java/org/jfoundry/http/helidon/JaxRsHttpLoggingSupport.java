package org.jfoundry.http.helidon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.http.HttpLoggingPolicy;

final class JaxRsHttpLoggingSupport {

    private JaxRsHttpLoggingSupport() {
    }

    static String describeBody(MediaType contentType, BodyCapture body) {
        if (!body.complete()) {
            return "<not fully consumed>";
        }
        if (body.truncated()) {
            return "<truncated at " + HttpLoggingPolicy.MAX_BODY_BYTES + " bytes>";
        }
        if (body.bytes().length == 0) {
            return "<empty>";
        }
        if (!isJson(contentType)) {
            return "<omitted: content-type=" + contentType + ">";
        }
        try (var reader = Json.createReader(new ByteArrayInputStream(body.bytes()))) {
            var json = reader.readValue();
            if (json.getValueType() != JsonValue.ValueType.OBJECT
                    && json.getValueType() != JsonValue.ValueType.ARRAY) {
                return "<omitted: JSON scalar>";
            }
            return redact(json).toString();
        } catch (RuntimeException exception) {
            return "<omitted: invalid JSON>";
        }
    }

    static boolean isJson(MediaType contentType) {
        return contentType != null && (MediaType.APPLICATION_JSON_TYPE.isCompatible(contentType)
                || contentType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json"));
    }

    private static JsonValue redact(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> redactObject(value.asJsonObject());
            case ARRAY -> redactArray(value.asJsonArray());
            default -> value;
        };
    }

    private static JsonObject redactObject(JsonObject object) {
        var builder = Json.createObjectBuilder();
        object.forEach((name, value) -> builder.add(name, HttpLoggingPolicy.isSensitiveJsonField(name)
                ? Json.createValue(HttpLoggingPolicy.REDACTED)
                : redact(value)));
        return builder.build();
    }

    private static JsonArray redactArray(JsonArray array) {
        var builder = Json.createArrayBuilder();
        array.forEach(value -> builder.add(redact(value)));
        return builder.build();
    }

    static final class BodyCapture {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private boolean complete;

        private boolean truncated;

        void capture(int value) {
            if (this.bytes.size() < HttpLoggingPolicy.MAX_BODY_BYTES) {
                this.bytes.write(value);
            } else {
                this.truncated = true;
            }
        }

        void capture(byte[] source, int offset, int length) {
            var remaining = HttpLoggingPolicy.MAX_BODY_BYTES - this.bytes.size();
            var retained = Math.min(Math.max(remaining, 0), length);
            this.bytes.write(source, offset, retained);
            if (retained < length) {
                this.truncated = true;
            }
        }

        void markComplete() {
            this.complete = true;
        }

        byte[] bytes() {
            return this.bytes.toByteArray();
        }

        boolean complete() {
            return this.complete;
        }

        boolean truncated() {
            return this.truncated;
        }
    }
}
