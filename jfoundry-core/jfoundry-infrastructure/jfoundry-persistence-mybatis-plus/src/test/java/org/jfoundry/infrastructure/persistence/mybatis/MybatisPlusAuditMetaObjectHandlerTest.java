package org.jfoundry.infrastructure.persistence.mybatis;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.jfoundry.infrastructure.persistence.AggregateData;
import org.jfoundry.infrastructure.persistence.AuditStamp;
import org.jfoundry.infrastructure.persistence.AuditStampHolder;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAuditMetaObjectHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:45:00Z");

    @Test
    void fillsAllTechnicalAuditFieldsOnInsert() {
        TestData data = new TestData();
        MybatisPlusAuditMetaObjectHandler handler = new MybatisPlusAuditMetaObjectHandler(
                new AuditStamping(Clock.fixed(NOW, ZoneOffset.UTC), () -> Optional.of("operator-42")));

        handler.insertFill(SystemMetaObject.forObject(data));

        assertThat(data.getCreatedAt()).isEqualTo(NOW);
        assertThat(data.getCreatedBy()).isEqualTo("operator-42");
        assertThat(data.getLastModifiedAt()).isEqualTo(NOW);
        assertThat(data.getLastModifiedBy()).isEqualTo("operator-42");
    }

    @Test
    void preservesCreationFieldsAndFillsOnlyModificationFieldsOnUpdate() {
        TestData data = new TestData();
        data.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        data.setCreatedBy("creator-7");
        data.setLastModifiedAt(Instant.parse("2026-07-15T00:00:00Z"));
        data.setLastModifiedBy("editor-3");
        MybatisPlusAuditMetaObjectHandler handler = new MybatisPlusAuditMetaObjectHandler(
                new AuditStamping(Clock.fixed(NOW, ZoneOffset.UTC), Optional::<String>empty));

        handler.updateFill(SystemMetaObject.forObject(data));

        assertThat(data.getCreatedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(data.getCreatedBy()).isEqualTo("creator-7");
        assertThat(data.getLastModifiedAt()).isEqualTo(NOW);
        assertThat(data.getLastModifiedBy()).isNull();
    }

    private static final class TestData extends AggregateData<String> implements AuditStampHolder {

        private Instant createdAt;
        private String createdBy;
        private Instant lastModifiedAt;
        private String lastModifiedBy;

        Instant getCreatedAt() {
            return createdAt;
        }

        void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        String getCreatedBy() {
            return createdBy;
        }

        void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        Instant getLastModifiedAt() {
            return lastModifiedAt;
        }

        void setLastModifiedAt(Instant lastModifiedAt) {
            this.lastModifiedAt = lastModifiedAt;
        }

        String getLastModifiedBy() {
            return lastModifiedBy;
        }

        void setLastModifiedBy(String lastModifiedBy) {
            this.lastModifiedBy = lastModifiedBy;
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
}
