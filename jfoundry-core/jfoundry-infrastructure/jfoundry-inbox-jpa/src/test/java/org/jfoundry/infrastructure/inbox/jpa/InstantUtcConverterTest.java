package org.jfoundry.infrastructure.inbox.jpa;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InstantUtcConverterTest {

    private final InstantUtcConverter converter = new InstantUtcConverter();

    @Test
    void preservesEpochWhenWritingAnInstantFromANonUtcOffset() {
        Instant instant = OffsetDateTime.parse("2026-07-16T20:34:56.123456+08:00").toInstant();

        Timestamp databaseValue = converter.convertToDatabaseColumn(instant);

        assertThat(databaseValue).isInstanceOf(Timestamp.class);
        assertThat(databaseValue.toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-07-16T12:34:56.123456"));
        assertThat(converter.convertToEntityAttribute(databaseValue)).isEqualTo(instant);
    }

    @Test
    void preservesNullValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
