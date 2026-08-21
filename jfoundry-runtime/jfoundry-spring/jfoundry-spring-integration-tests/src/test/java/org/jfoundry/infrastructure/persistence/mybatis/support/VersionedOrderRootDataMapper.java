package org.jfoundry.infrastructure.persistence.mybatis.support;

public final class VersionedOrderRootDataMapper {

    public VersionedOrderData toData(TestOrder entity) {
        VersionedOrderData data = new VersionedOrderData();
        data.setId(toDataId(entity.getId()));
        data.setStatus(entity.getStatus().name());
        data.setAmount(entity.getAmount());
        data.setCreatedAt(entity.getCreatedAt());
        data.setUpdatedAt(entity.getUpdatedAt());
        return data;
    }

    public String toDataId(TestOrderId id) {
        return id == null ? null : id.value();
    }
}
