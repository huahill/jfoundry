package org.jfoundry.quarkus.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "jfoundry_quarkus_order")
public class QuarkusJpaOrderEntity {

    @Id
    private String id;
    private String status;

    protected QuarkusJpaOrderEntity() {
    }

    QuarkusJpaOrderEntity(String id, String status) {
        this.id = id;
        this.status = status;
    }

    String id() {
        return id;
    }

    String status() {
        return status;
    }

    void status(String status) {
        this.status = status;
    }
}
