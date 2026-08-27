package org.jfoundry.infrastructure.persistence.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

/// Produces the default technical audit service for Quarkus applications.
@ApplicationScoped
public final class QuarkusAuditStampingProducer {

    @Produces
    @DefaultBean
    AuditStamping auditStamping(Instance<AuditActorProvider> actorProviders) {
        return new AuditStamping(Clock.system(ZoneOffset.UTC), actorProvider(actorProviders));
    }

    private static AuditActorProvider actorProvider(Instance<AuditActorProvider> actorProviders) {
        return actorProviders.isUnsatisfied() ? Optional::<String>empty : actorProviders.get();
    }
}
