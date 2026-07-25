package org.jfoundry.infrastructure.outbox.jpa;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InstantUtcConverterTest {

    private final InstantUtcConverter converter = new InstantUtcConverter();

    @Test
    void roundTripsInstantsAsUtcLocalDateTimes() {
        Instant instant = Instant.parse("2026-07-16T12:34:56.123456Z");

        Timestamp databaseValue = converter.convertToDatabaseColumn(instant);

        assertThat(databaseValue).isInstanceOf(Timestamp.class);
        assertThat(databaseValue.toLocalDateTime())
                .isEqualTo(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
        assertThat(converter.convertToEntityAttribute(databaseValue)).isEqualTo(instant);
    }

    @Test
    void preservesNullValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
