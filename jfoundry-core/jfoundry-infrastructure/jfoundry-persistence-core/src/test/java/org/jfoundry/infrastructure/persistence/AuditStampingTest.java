package org.jfoundry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditStampingTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:30:00Z");

    @Test
    void stampsCreationAndModificationWithTheCurrentActorOnInsert() {
        AuditStamping stamping = new AuditStamping(Clock.fixed(NOW, ZoneOffset.UTC),
                () -> Optional.of("operator-42"));

        assertThat(stamping.stampForInsert()).isEqualTo(new AuditStamp(
                NOW, "operator-42", NOW, "operator-42"));
    }

    @Test
    void preservesCreationMetadataAndOnlyRestampsModificationOnUpdate() {
        AuditStamp existing = new AuditStamp(
                Instant.parse("2026-07-01T00:00:00Z"), "creator-7",
                Instant.parse("2026-07-15T00:00:00Z"), "editor-3");
        AuditStamping stamping = new AuditStamping(Clock.fixed(NOW, ZoneOffset.UTC), Optional::<String>empty);

        assertThat(stamping.stampForUpdate(existing)).isEqualTo(new AuditStamp(
                existing.createdAt(), existing.createdBy(), NOW, null));
    }
}
