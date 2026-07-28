package org.jfoundry.infrastructure.persistence;

import java.time.Instant;
import java.util.Objects;

/// Technical audit metadata stored with a persistence snapshot.
public record AuditStamp(
        Instant createdAt,
        String createdBy,
        Instant lastModifiedAt,
        String lastModifiedBy) {

    public AuditStamp {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");
    }
}
