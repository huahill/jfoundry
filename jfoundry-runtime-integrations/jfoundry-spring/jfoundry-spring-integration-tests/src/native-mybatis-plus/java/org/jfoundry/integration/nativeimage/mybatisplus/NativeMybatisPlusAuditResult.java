package org.jfoundry.integration.nativeimage.mybatisplus;

/// Observable result of the MyBatis-Plus Native Image persistence operation.
record NativeMybatisPlusAuditResult(
        boolean createdAtSet,
        boolean lastModifiedAtSet,
        String createdBy,
        String lastModifiedBy,
        String value) {
}
