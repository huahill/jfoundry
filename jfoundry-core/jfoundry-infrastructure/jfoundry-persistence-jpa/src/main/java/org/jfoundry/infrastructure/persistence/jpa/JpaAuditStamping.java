package org.jfoundry.infrastructure.persistence.jpa;

import org.jfoundry.infrastructure.persistence.AuditStamping;

import java.util.Objects;

/// Applies technical audit stamps at Jakarta Persistence repository lifecycle boundaries.
public final class JpaAuditStamping {

    private final AuditStamping auditStamping;

    public JpaAuditStamping(AuditStamping auditStamping) {
        this.auditStamping = Objects.requireNonNull(auditStamping, "auditStamping must not be null");
    }

    /// Applies creation and modification metadata before persisting a new entity.
    public void stampForPersist(JpaAuditData data) {
        Objects.requireNonNull(data, "data must not be null");
        data.applyAuditStamp(auditStamping.stampForInsert());
    }

    /// Applies modification metadata before flushing an existing entity update.
    public void stampForUpdate(JpaAuditData data) {
        Objects.requireNonNull(data, "data must not be null");
        data.applyAuditStamp(auditStamping.stampForUpdate(data.auditStamp()));
    }
}
