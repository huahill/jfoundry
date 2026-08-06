package org.jfoundry.quarkus.integration;

import org.jfoundry.domain.entity.agg.BaseAggregateRoot;

final class QuarkusJpaOrder extends BaseAggregateRoot<QuarkusJpaOrder, QuarkusJpaOrderId> {

    private String status;

    private QuarkusJpaOrder(QuarkusJpaOrderId id, String status) {
        super(id);
        this.status = status;
    }

    static QuarkusJpaOrder create(String id) {
        return new QuarkusJpaOrder(new QuarkusJpaOrderId(id), "CREATED");
    }

    static QuarkusJpaOrder restore(QuarkusJpaOrderId id, String status) {
        return new QuarkusJpaOrder(id, status);
    }

    void markPaid() {
        status = "PAID";
    }

    String status() {
        return status;
    }
}
