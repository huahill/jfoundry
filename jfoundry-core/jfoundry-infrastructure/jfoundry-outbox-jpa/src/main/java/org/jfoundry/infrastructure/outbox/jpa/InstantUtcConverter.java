package org.jfoundry.infrastructure.outbox.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/// Persists Instants in UTC through SQL TIMESTAMP columns without timezone information.
@Converter(autoApply = false)
public final class InstantUtcConverter implements AttributeConverter<Instant, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : Timestamp.valueOf(LocalDateTime.ofInstant(attribute, ZoneOffset.UTC));
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp databaseValue) {
        return databaseValue == null ? null : databaseValue.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
