package org.jfoundry.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import org.jfoundry.infrastructure.persistence.AggregateData;
import org.jfoundry.infrastructure.persistence.AuditStamp;

import java.io.Serializable;
import java.time.Instant;

/// MyBatis-Plus persistence base class with technical audit snapshot fields.
///
/// @param <ID> persistence identifier type
public abstract class MybatisPlusAuditData<ID extends Serializable> extends AggregateData<ID> {

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant lastModifiedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String lastModifiedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(Instant lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
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
