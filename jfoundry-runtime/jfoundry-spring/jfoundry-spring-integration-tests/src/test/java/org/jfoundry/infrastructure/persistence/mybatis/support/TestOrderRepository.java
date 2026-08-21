package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAggregateRepository;

/// Test repository implemented as a concrete MybatisPlusAggregateRepository subclass.
public class TestOrderRepository
        extends MybatisPlusAggregateRepository<TestOrder, TestOrderId, TestOrderData, String> {

    public TestOrderRepository(TestOrderMapper mapper,
                                TestOrderDataMapper dataMapper) {
        super(mapper, dataMapper);
    }
}
