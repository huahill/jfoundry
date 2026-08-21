package org.jfoundry.infrastructure.persistence.jpa.support;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "jpa_graph_order_line")
public class GraphOrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private GraphOrderEntity order;

    @jakarta.persistence.Column(nullable = false)
    private String sku;

    protected GraphOrderLineEntity() {
    }

    public GraphOrderLineEntity(GraphOrderEntity order, String sku) {
        this.order = order;
        this.sku = sku;
    }

    public Long id() {
        return id;
    }

    public GraphOrderEntity order() {
        return order;
    }

    public String sku() {
        return sku;
    }

    public void replaceSku(String sku) {
        this.sku = sku;
    }
}
