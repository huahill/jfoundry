package org.jfoundry.quarkus.integration;

import org.jfoundry.infrastructure.persistence.jpa.JpaAggregateMapper;

final class QuarkusJpaOrderMapper
        implements JpaAggregateMapper<QuarkusJpaOrder, QuarkusJpaOrderId, QuarkusJpaOrderEntity, String> {

    @Override
    public String toEntityId(QuarkusJpaOrderId id) {
        return id.value();
    }

    @Override
    public QuarkusJpaOrderEntity newEntity(QuarkusJpaOrder aggregate) {
        return new QuarkusJpaOrderEntity(aggregate.getId().value(), aggregate.status());
    }

    @Override
    public QuarkusJpaOrder toAggregate(QuarkusJpaOrderEntity entity) {
        return QuarkusJpaOrder.restore(new QuarkusJpaOrderId(entity.id()), entity.status());
    }

    @Override
    public void apply(QuarkusJpaOrder aggregate, QuarkusJpaOrderEntity managedEntity) {
        managedEntity.status(aggregate.status());
    }
}
