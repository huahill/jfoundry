package io.huahill.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unrelated_test_entity")
class UnrelatedTestEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String description;

    protected UnrelatedTestEntity() {
    }

    UnrelatedTestEntity(String id, String description) {
        this.id = id;
        this.description = description;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }
}
