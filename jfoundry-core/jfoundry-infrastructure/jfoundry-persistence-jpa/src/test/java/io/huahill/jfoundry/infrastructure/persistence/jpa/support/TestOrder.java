package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import io.huahill.jfoundry.domain.entity.agg.BaseAggregateRoot;

public final class TestOrder extends BaseAggregateRoot<TestOrder, TestOrderId> {

    private String status;

    private TestOrder(TestOrderId id, String status) {
        super(id);
        this.status = status;
    }

    public static TestOrder create(String id) {
        return new TestOrder(new TestOrderId(id), "CREATED");
    }

    public static TestOrder restore(TestOrderId id, String status) {
        return new TestOrder(id, status);
    }

    public void markPaid() {
        status = "PAID";
    }

    public void cancel() {
        status = "CANCELLED";
    }

    public String status() {
        return status;
    }
}
