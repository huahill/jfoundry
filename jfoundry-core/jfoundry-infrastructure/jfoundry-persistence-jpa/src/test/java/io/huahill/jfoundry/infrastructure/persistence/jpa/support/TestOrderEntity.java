package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "jpa_test_order")
public class TestOrderEntity {

    @Id
    private String id;
    private String status;

    @Version
    private long version;

    protected TestOrderEntity() {
    }

    public TestOrderEntity(String id, String status) {
        this.id = id;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String status() {
        return status;
    }

    public void status(String status) {
        this.status = status;
    }

    public long version() {
        return version;
    }
}
