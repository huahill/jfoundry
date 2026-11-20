package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphOrderTest {

    @Test
    void recordsCreationAndLineReplacementEvents() {
        GraphOrder order = GraphOrder.create(new GraphOrderId("GRAPH-EVENTS"), List.of("A", "B"));
        order.replaceLines(List.of("B", "C"));

        assertThat(order.drainEvents()).containsExactly(
                new GraphOrderCreated(new GraphOrderId("GRAPH-EVENTS")),
                new GraphOrderLinesReplaced(new GraphOrderId("GRAPH-EVENTS"), List.of("B", "C")));
    }
}
