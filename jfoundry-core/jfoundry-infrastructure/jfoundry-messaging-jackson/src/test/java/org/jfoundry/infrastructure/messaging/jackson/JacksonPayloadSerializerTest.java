package org.jfoundry.infrastructure.messaging.jackson;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonPayloadSerializerTest {

    private final JacksonPayloadSerializer serializer = new JacksonPayloadSerializer(new ObjectMapper());

    @Test
    void serializesPortableJsonWithoutJavaTypeMetadata() throws Exception {
        SerializerTestEvent event = new SerializerTestEvent(
                Instant.parse("2026-06-18T10:00:00Z"), new BigDecimal("1000.00"));

        String json = serializer.serialize(event);

        assertThat(json).contains("\"occurredAt\":\"2026-06-18T10:00:00Z\"");
        assertThat(json).contains("\"amount\":1000.00");
        assertThat(json).doesNotContain("@class", "java.math.BigDecimal");

        ObjectMapper consumerMapper = new ObjectMapper();
        assertThat(consumerMapper.readValue(json, SerializerTestEvent.class)).isEqualTo(event);
    }

    record SerializerTestEvent(Instant occurredAt, BigDecimal amount) {
    }
}
