package org.jfoundry.infrastructure.persistence.jpa.support;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "jpa_graph_order")
public class GraphOrderEntity {

    @Id
    private String id;

    @Version
    private long version;

    @Column(nullable = false)
    private long graphRevision;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<GraphOrderLineEntity> lines = new ArrayList<>();

    protected GraphOrderEntity() {
    }

    public GraphOrderEntity(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public long version() {
        return version;
    }

    public List<String> lineSkus() {
        return lines.stream().map(GraphOrderLineEntity::sku).toList();
    }

    public void replaceLines(Collection<String> skus) {
        lines.clear();
        skus.forEach(sku -> lines.add(new GraphOrderLineEntity(this, sku)));
    }

    public void replaceFirstLineSku(String sku) {
        lines.getFirst().replaceSku(sku);
    }

    public void touchForGraphMutation() {
        graphRevision++;
    }
}
