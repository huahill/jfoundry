package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;

/// Provides the JPA-backed Outbox store for Helidon applications.
@Dependent
public final class HelidonJpaOutboxMessageStoreProducer {

    @Produces
    OutboxMessageStore outboxMessageStore(EntityManager entityManager) {
        return new JpaOutboxMessageStore(entityManager);
    }
}
