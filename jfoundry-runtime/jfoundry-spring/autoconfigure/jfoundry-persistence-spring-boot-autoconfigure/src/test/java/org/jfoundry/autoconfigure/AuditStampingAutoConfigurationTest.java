package org.jfoundry.autoconfigure;

import org.jfoundry.autoconfigure.persistence.AuditStampingAutoConfiguration;
import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditStampingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditStampingAutoConfiguration.class));

    @Test
    void registersUtcClockAndEmptyActorProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditStamping.class);
            assertThat(context).hasSingleBean(AuditActorProvider.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(context.getBean(AuditStamping.class).stampForInsert().createdBy()).isNull();
        });
    }

    @Test
    void usesApplicationClockAndActorProvider() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC);
        AuditActorProvider actorProvider = () -> Optional.of("operator-42");

        contextRunner.withBean(Clock.class, () -> clock)
                .withBean(AuditActorProvider.class, () -> actorProvider)
                .run(context -> {
                    var stamp = context.getBean(AuditStamping.class).stampForInsert();

                    assertThat(stamp.createdAt()).isEqualTo(clock.instant());
                    assertThat(stamp.createdBy()).isEqualTo("operator-42");
                });
    }

    @Test
    void backsOffWhenApplicationProvidesAuditStamping() {
        AuditStamping custom = new AuditStamping(
                Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC),
                Optional::<String>empty);

        contextRunner.withBean(AuditStamping.class, () -> custom)
                .run(context -> assertThat(context.getBean(AuditStamping.class)).isSameAs(custom));
    }
}
