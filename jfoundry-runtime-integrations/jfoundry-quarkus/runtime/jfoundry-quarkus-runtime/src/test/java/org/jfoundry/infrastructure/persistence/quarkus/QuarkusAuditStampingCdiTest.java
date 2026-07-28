package org.jfoundry.infrastructure.persistence.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class QuarkusAuditStampingCdiTest {

    @Inject
    AuditStamping auditStamping;

    @Test
    void usesApplicationActorProviderWithTheDefaultAuditStamping() {
        assertThat(auditStamping.stampForInsert().createdBy()).isEqualTo("operator-42");
    }

    @ApplicationScoped
    public static class ApplicationActorProvider implements AuditActorProvider {

        @Override
        public Optional<String> currentActorId() {
            return Optional.of("operator-42");
        }
    }
}
