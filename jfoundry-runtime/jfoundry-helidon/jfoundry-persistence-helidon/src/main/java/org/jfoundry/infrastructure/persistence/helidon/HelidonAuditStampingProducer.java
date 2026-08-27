package org.jfoundry.infrastructure.persistence.helidon;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.jfoundry.infrastructure.persistence.AuditStamping;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

/// Produces the replaceable default technical audit service for Helidon applications.
@Alternative
@Priority(1)
@Dependent
public final class HelidonAuditStampingProducer {

    @Produces
    AuditStamping auditStamping(Instance<AuditActorProvider> actorProviders) {
        return new AuditStamping(Clock.system(ZoneOffset.UTC), actorProvider(actorProviders));
    }

    private static AuditActorProvider actorProvider(Instance<AuditActorProvider> actorProviders) {
        return actorProviders.isUnsatisfied() ? Optional::<String>empty : actorProviders.get();
    }
}
