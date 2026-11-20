package io.huahill.jfoundry.infrastructure.outbox.quarkus.jpa;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;

/// Provides the default JPA-backed Outbox store for Quarkus applications.
@ApplicationScoped
public final class QuarkusJpaOutboxMessageStoreProducer {

    @Produces
    @DefaultBean
    OutboxMessageStore outboxMessageStore(EntityManager entityManager) {
        return new JpaOutboxMessageStore(entityManager);
    }
}
