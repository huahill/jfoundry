package io.huahill.jfoundry.quarkus.integration;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import io.huahill.jfoundry.infrastructure.persistence.jpa.JpaAggregateRepository;

@Singleton
public class QuarkusJpaOrderRepository
        extends JpaAggregateRepository<QuarkusJpaOrder, QuarkusJpaOrderId, QuarkusJpaOrderEntity, String> {

    public QuarkusJpaOrderRepository(EntityManager entityManager) {
        super(entityManager, QuarkusJpaOrderEntity.class, new QuarkusJpaOrderMapper());
    }
}
