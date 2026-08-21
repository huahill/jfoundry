package org.jfoundry.infrastructure.persistence.jpa.support;

import org.jfoundry.infrastructure.persistence.jpa.JpaAggregateMapper;

public final class GraphOrderMapper
        implements JpaAggregateMapper<GraphOrder, GraphOrderId, GraphOrderEntity, String> {

    @Override
    public String toEntityId(GraphOrderId id) {
        return id.value();
    }

    @Override
    public GraphOrderEntity newEntity(GraphOrder aggregate) {
        GraphOrderEntity entity = new GraphOrderEntity(aggregate.getId().value());
        entity.replaceLines(aggregate.lineSkus());
        return entity;
    }

    @Override
    public GraphOrder toAggregate(GraphOrderEntity entity) {
        return GraphOrder.restore(new GraphOrderId(entity.id()), entity.lineSkus());
    }

    @Override
    public void apply(GraphOrder aggregate, GraphOrderEntity managedEntity) {
        managedEntity.replaceLines(aggregate.lineSkus());
        managedEntity.touchForGraphMutation();
    }
}
