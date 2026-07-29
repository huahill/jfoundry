package org.jfoundry.infrastructure.messaging.jackson;

import org.jfoundry.application.messaging.PayloadSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/// Default Outbox payload serializer based on Jackson 3.
/// <p>
/// Jackson 3 serializes Java Time types as ISO-8601 strings by default. The serializer deliberately
/// does not enable default typing: integration payloads must not expose Java class names, and the
/// Outbox record already carries an explicit payload type alongside the JSON body.
/// <p>
/// Applications can replace serialization by registering their own {@link PayloadSerializer} bean.
public class JacksonPayloadSerializer implements PayloadSerializer {

    private final ObjectMapper objectMapper;

    public JacksonPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event payload: " + event.getClass().getName(), e);
        }
    }
}
