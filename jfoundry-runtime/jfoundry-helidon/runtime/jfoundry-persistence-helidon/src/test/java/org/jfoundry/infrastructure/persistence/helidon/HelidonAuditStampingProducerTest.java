package org.jfoundry.infrastructure.persistence.helidon;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonAuditStampingProducerTest {

    @Test
    void exposesAReplaceableCdiDefault() {
        assertThat(HelidonAuditStampingProducer.class.isAnnotationPresent(Alternative.class)).isTrue();
        assertThat(HelidonAuditStampingProducer.class.isAnnotationPresent(Priority.class)).isTrue();
    }

    @Test
    void usesApplicationActorProvider() {
        withContainer(ApplicationActorProvider.class, container -> assertThat(container.select(AuditStamping.class)
                .get()
                .stampForInsert()
                .createdBy()).isEqualTo("operator-42"));
    }

    @Test
    void applicationAuditStampingOverridesTheDefault() {
        withContainer(ApplicationAuditStampingProducer.class, container -> assertThat(container.select(AuditStamping.class)
                .get()
                .stampForInsert()
                .createdBy()).isEqualTo("application"));
    }

    private static void withContainer(Class<?> applicationBean, java.util.function.Consumer<SeContainer> assertion) {
        String previous = System.getProperty("mp.initializer.allow");
        System.setProperty("mp.initializer.allow", "true");
        try {
            try (SeContainer container = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addBeanClasses(HelidonAuditStampingProducer.class, applicationBean)
                    .initialize()) {
                assertion.accept(container);
            }
        } finally {
            if (previous == null) {
                System.clearProperty("mp.initializer.allow");
            } else {
                System.setProperty("mp.initializer.allow", previous);
            }
        }
    }

    @Dependent
    static final class ApplicationActorProvider implements AuditActorProvider {

        @Override
        public Optional<String> currentActorId() {
            return Optional.of("operator-42");
        }
    }

    @Dependent
    @Alternative
    @Priority(2)
    static final class ApplicationAuditStampingProducer {

        @Produces
        AuditStamping auditStamping() {
            return new AuditStamping(
                    Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC),
                    () -> Optional.of("application"));
        }
    }
}
