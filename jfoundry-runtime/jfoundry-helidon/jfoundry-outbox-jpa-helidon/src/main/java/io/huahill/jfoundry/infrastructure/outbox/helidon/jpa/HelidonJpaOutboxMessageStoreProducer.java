package io.huahill.jfoundry.infrastructure.outbox.helidon.jpa;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;

/// Provides the JPA-backed Outbox store for Helidon applications.
@Dependent
public final class HelidonJpaOutboxMessageStoreProducer {

    @Produces
    OutboxMessageStore outboxMessageStore(EntityManager entityManager) {
        return new JpaOutboxMessageStore(entityManager);
    }
}
