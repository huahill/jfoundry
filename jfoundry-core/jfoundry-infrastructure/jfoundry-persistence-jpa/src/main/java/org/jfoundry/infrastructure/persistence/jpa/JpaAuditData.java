package org.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.jfoundry.infrastructure.persistence.AuditStamp;

import java.time.Instant;

/// Jakarta Persistence mapped superclass for technical audit snapshot fields.
@MappedSuperclass
public abstract class JpaAuditData {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    /// Returns the current technical audit snapshot.
    public AuditStamp auditStamp() {
        return new AuditStamp(createdAt, createdBy, lastModifiedAt, lastModifiedBy);
    }

    /// Replaces all technical audit fields with the supplied snapshot.
    public void applyAuditStamp(AuditStamp auditStamp) {
        this.createdAt = auditStamp.createdAt();
        this.createdBy = auditStamp.createdBy();
        this.lastModifiedAt = auditStamp.lastModifiedAt();
        this.lastModifiedBy = auditStamp.lastModifiedBy();
    }
}
