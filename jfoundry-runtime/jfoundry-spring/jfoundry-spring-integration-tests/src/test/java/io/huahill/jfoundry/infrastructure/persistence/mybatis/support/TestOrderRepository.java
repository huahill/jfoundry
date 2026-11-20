package io.huahill.jfoundry.infrastructure.persistence.mybatis.support;

import io.huahill.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAggregateRepository;

/// Test repository implemented as a concrete MybatisPlusAggregateRepository subclass.
public class TestOrderRepository
        extends MybatisPlusAggregateRepository<TestOrder, TestOrderId, TestOrderData, String> {

    public TestOrderRepository(TestOrderMapper mapper,
                                TestOrderDataMapper dataMapper) {
        super(mapper, dataMapper);
    }
}
