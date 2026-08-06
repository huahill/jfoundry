package org.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;

/// Provides the default JPA-backed Outbox store for Quarkus applications.
@ApplicationScoped
public final class QuarkusJpaOutboxMessageStoreProducer {

    @Produces
    @DefaultBean
    OutboxMessageStore outboxMessageStore(EntityManager entityManager) {
        return new JpaOutboxMessageStore(entityManager);
    }
}
