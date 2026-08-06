package org.jfoundry.integration.nativeimage.mybatisplus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.jfoundry.infrastructure.persistence.AggregateData;
import org.jfoundry.infrastructure.persistence.AuditStamp;
import org.jfoundry.infrastructure.persistence.AuditStampHolder;

import java.time.Instant;

/// Minimal persistence type that exercises MyBatis-Plus field filling in a Native Image consumer.
@TableName("native_audit_record")
public class NativeAuditRecord extends AggregateData<String> implements AuditStampHolder {

    @TableId(type = IdType.INPUT)
    private String id;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant lastModifiedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String lastModifiedBy;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

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

    @Override
    public AuditStamp auditStamp() {
        return new AuditStamp(createdAt, createdBy, lastModifiedAt, lastModifiedBy);
    }

    @Override
    public void applyAuditStamp(AuditStamp auditStamp) {
        createdAt = auditStamp.createdAt();
        createdBy = auditStamp.createdBy();
        lastModifiedAt = auditStamp.lastModifiedAt();
        lastModifiedBy = auditStamp.lastModifiedBy();
    }
}
