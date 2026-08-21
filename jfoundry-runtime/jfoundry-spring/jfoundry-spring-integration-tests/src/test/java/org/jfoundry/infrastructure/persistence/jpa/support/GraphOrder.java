package org.jfoundry.infrastructure.persistence.jpa.support;

import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;

public final class GraphOrder extends BaseAggregateRoot<GraphOrder, GraphOrderId> {

    private List<String> lineSkus;

    private GraphOrder(GraphOrderId id, List<String> lineSkus) {
        super(id);
        this.lineSkus = List.copyOf(lineSkus);
    }

    public static GraphOrder create(GraphOrderId id, List<String> lineSkus) {
        GraphOrder order = new GraphOrder(id, lineSkus);
        order.recordEvent(new GraphOrderCreated(id));
        return order;
    }

    public static GraphOrder restore(GraphOrderId id, List<String> lineSkus) {
        return new GraphOrder(id, lineSkus);
    }

    public void replaceLines(List<String> lineSkus) {
        this.lineSkus = List.copyOf(lineSkus);
        recordEvent(new GraphOrderLinesReplaced(getId(), this.lineSkus));
    }

    public List<String> lineSkus() {
        return lineSkus;
    }
}

record GraphOrderCreated(GraphOrderId orderId) implements DomainEvent {
}

record GraphOrderLinesReplaced(GraphOrderId orderId, List<String> lineSkus) implements DomainEvent {

    GraphOrderLinesReplaced {
        lineSkus = List.copyOf(lineSkus);
    }
}
