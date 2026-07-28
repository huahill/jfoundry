package org.jfoundry.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// Applies consistent technical audit semantics to persistence snapshots.
public final class AuditStamping {

    private final Clock clock;
    private final AuditActorProvider actorProvider;

    public AuditStamping(Clock clock, AuditActorProvider actorProvider) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.actorProvider = Objects.requireNonNull(actorProvider, "actorProvider must not be null");
    }

    /// Creates the audit stamp for a new persistence snapshot.
    public AuditStamp stampForInsert() {
        Instant now = clock.instant();
        String actorId = actorId();
        return new AuditStamp(now, actorId, now, actorId);
    }

    /// Preserves creation metadata while updating modification metadata.
    public AuditStamp stampForUpdate(AuditStamp existing) {
        Objects.requireNonNull(existing, "existing must not be null");
        return new AuditStamp(existing.createdAt(), existing.createdBy(), clock.instant(), actorId());
    }

    private String actorId() {
        return actorProvider.currentActorId()
                .filter(actorId -> !actorId.isBlank())
                .orElse(null);
    }
}
