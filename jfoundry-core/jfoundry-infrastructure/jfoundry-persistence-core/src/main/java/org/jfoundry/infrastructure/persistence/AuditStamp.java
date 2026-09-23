package org.jfoundry.infrastructure.persistence;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Technical audit metadata stored with a persistence snapshot.
public record AuditStamp(
        Instant createdAt,
        @Nullable String createdBy,
        Instant lastModifiedAt,
        @Nullable String lastModifiedBy) {

    public AuditStamp {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");
    }
}
