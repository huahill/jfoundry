package org.jfoundry.integration.nativeimage.mybatisplus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/// HTTP operation used by the Native Image integration test to exercise MyBatis-Plus persistence.
@RestController
class NativeMybatisPlusResource {

    private final NativeAuditRecordMapper mapper;

    NativeMybatisPlusResource(NativeAuditRecordMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/jfoundry/native/mybatis-plus/ready")
    String ready() {
        return "ready";
    }

    @PostMapping("/jfoundry/native/mybatis-plus/audit-record")
    NativeMybatisPlusAuditResult persistAndUpdateAuditRecord() {
        NativeAuditRecord record = new NativeAuditRecord();
        record.setId("native-audit-record");
        record.setContent("created");
        mapper.insert(record);

        NativeAuditRecord persisted = mapper.selectById(record.getId());
        persisted.setContent("updated");
        mapper.updateById(persisted);

        NativeAuditRecord updated = mapper.selectById(record.getId());
        return new NativeMybatisPlusAuditResult(
                updated.getCreatedAt() != null,
                updated.getLastModifiedAt() != null,
                updated.getCreatedBy(),
                updated.getLastModifiedBy(),
                updated.getContent());
    }
}
