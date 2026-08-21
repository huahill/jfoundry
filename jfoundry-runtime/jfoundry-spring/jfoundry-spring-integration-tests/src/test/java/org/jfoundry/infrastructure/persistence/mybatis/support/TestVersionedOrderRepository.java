package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAggregateRepository;

public final class TestVersionedOrderRepository extends
        MybatisPlusAggregateRepository<TestOrder, TestOrderId, VersionedOrderData, String> {

    private static final VersionedOrderRootDataMapper ROOT_DATA_MAPPER =
            new VersionedOrderRootDataMapper();

    private int completedUpdates;
    private int completedRemovals;
    private boolean failNextUpdate;

    public TestVersionedOrderRepository(VersionedOrderMapper mapper) {
        super(
                mapper,
                ROOT_DATA_MAPPER::toData,
                ROOT_DATA_MAPPER::toDataId,
                VersionedOrderData.class);
    }

    @Override
    protected TestOrder doFindById(TestOrderId id) {
        return loadAggregate(id, TestVersionedOrderRepository::restore);
    }

    @Override
    protected void doAdd(TestOrder aggregate) {
        insertAggregate(aggregate, ROOT_DATA_MAPPER.toData(aggregate), ignored -> { });
    }

    @Override
    protected void doModify(TestOrder aggregate) {
        updateAggregate(aggregate, ROOT_DATA_MAPPER.toData(aggregate), ignored -> {
            if (failNextUpdate) {
                failNextUpdate = false;
                throw new IllegalStateException("Additional update failed.");
            }
            completedUpdates++;
        });
    }

    @Override
    protected void doRemove(TestOrder aggregate) {
        deleteAggregate(aggregate, () -> completedRemovals++);
    }

    public int completedUpdates() {
        return completedUpdates;
    }

    public void resetCompletedUpdates() {
        completedUpdates = 0;
    }

    public void failNextUpdate() {
        failNextUpdate = true;
    }

    public int completedRemovals() {
        return completedRemovals;
    }

    private static TestOrder restore(VersionedOrderData data) {
        return TestOrder.restore(
                new TestOrderId(data.getId()),
                TestOrderStatus.valueOf(data.getStatus()),
                data.getAmount(),
                data.getCreatedAt(),
                data.getUpdatedAt());
    }
}
