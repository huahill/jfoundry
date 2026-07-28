package org.jfoundry.autoconfigure.persistence;

import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

/// Configures the default technical audit dependencies for Spring applications.
@AutoConfiguration
public class AuditStampingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock auditClock() {
        return Clock.system(ZoneOffset.UTC);
    }

    @Bean
    @ConditionalOnMissingBean(AuditActorProvider.class)
    AuditActorProvider auditActorProvider() {
        return Optional::<String>empty;
    }

    @Bean
    @ConditionalOnMissingBean(AuditStamping.class)
    AuditStamping auditStamping(Clock clock, AuditActorProvider actorProvider) {
        return new AuditStamping(clock, actorProvider);
    }
}
