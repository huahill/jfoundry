package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import io.huahill.jfoundry.infrastructure.persistence.jpa.JpaAggregateMapper;

/// Test mapper that changes an existing child entity and explicitly touches the versioned root.
public final class ChildGraphMutationMapper
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
        managedEntity.replaceFirstLineSku(aggregate.lineSkus().getFirst());
        managedEntity.touchForGraphMutation();
    }
}
