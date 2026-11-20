package io.huahill.jfoundry.infrastructure.persistence.mybatis.support;

import io.huahill.jfoundry.infrastructure.persistence.mybatis.MybatisPlusDataMapper;

/// Test mapper between aggregate roots and Data objects.
public class TestOrderDataMapper implements MybatisPlusDataMapper<TestOrder, TestOrderId, TestOrderData, String> {

    @Override
    public TestOrderData toData(TestOrder entity) {
        TestOrderData data = new TestOrderData();
        data.setId(toDataId(entity.getId()));
        data.setStatus(entity.getStatus().name());
        data.setAmount(entity.getAmount());
        data.setCreatedAt(entity.getCreatedAt());
        data.setUpdatedAt(entity.getUpdatedAt());
        return data;
    }

    @Override
    public TestOrder toEntity(TestOrderData data) {
        return TestOrder.restore(
                new TestOrderId(data.getId()),
                TestOrderStatus.valueOf(data.getStatus()),
                data.getAmount(),
                data.getCreatedAt(),
                data.getUpdatedAt()
        );
    }

    @Override
    public String toDataId(TestOrderId id) {
        return id == null ? null : id.value();
    }
}
