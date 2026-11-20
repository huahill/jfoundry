package io.huahill.jfoundry.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import io.huahill.jfoundry.infrastructure.persistence.AuditStampHolder;
import io.huahill.jfoundry.infrastructure.persistence.AuditStamping;

import java.util.Objects;

/// Applies technical audit stamps to opted-in MyBatis-Plus persistence snapshots.
public final class MybatisPlusAuditMetaObjectHandler implements MetaObjectHandler {

    private final AuditStamping auditStamping;

    public MybatisPlusAuditMetaObjectHandler(AuditStamping auditStamping) {
        this.auditStamping = Objects.requireNonNull(auditStamping, "auditStamping must not be null");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        auditData(metaObject).ifPresent(data -> data.applyAuditStamp(auditStamping.stampForInsert()));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        auditData(metaObject).ifPresent(data -> data.applyAuditStamp(auditStamping.stampForUpdate(data.auditStamp())));
    }

    private static java.util.Optional<AuditStampHolder> auditData(MetaObject metaObject) {
        Object originalObject = metaObject.getOriginalObject();
        return originalObject instanceof AuditStampHolder auditData
                ? java.util.Optional.of(auditData)
                : java.util.Optional.empty();
    }
}
