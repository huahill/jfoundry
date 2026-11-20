package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import io.huahill.jfoundry.infrastructure.persistence.jpa.JpaAggregateMapper;

public final class TestOrderMapper
        implements JpaAggregateMapper<TestOrder, TestOrderId, TestOrderEntity, String> {

    private TestOrderEntity lastAppliedEntity;

    @Override
    public String toEntityId(TestOrderId id) {
        return id.value();
    }

    @Override
    public TestOrderEntity newEntity(TestOrder aggregate) {
        return new TestOrderEntity(aggregate.getId().value(), aggregate.status());
    }

    @Override
    public TestOrder toAggregate(TestOrderEntity entity) {
        return TestOrder.restore(new TestOrderId(entity.id()), entity.status());
    }

    @Override
    public void apply(TestOrder aggregate, TestOrderEntity managedEntity) {
        lastAppliedEntity = managedEntity;
        managedEntity.status(aggregate.status());
    }

    public TestOrderEntity lastAppliedEntity() {
        return lastAppliedEntity;
    }
}
