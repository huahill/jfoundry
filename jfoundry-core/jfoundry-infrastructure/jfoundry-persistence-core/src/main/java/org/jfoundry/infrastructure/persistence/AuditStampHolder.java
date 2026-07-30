package org.jfoundry.infrastructure.persistence;

/// Exposes technical audit metadata held by a persistence snapshot.
///
/// Implementations define their own storage fields and mapping annotations.
public interface AuditStampHolder {

    /// Returns the current technical audit metadata.
    AuditStamp auditStamp();

    /// Replaces the current technical audit metadata.
    void applyAuditStamp(AuditStamp auditStamp);
}
