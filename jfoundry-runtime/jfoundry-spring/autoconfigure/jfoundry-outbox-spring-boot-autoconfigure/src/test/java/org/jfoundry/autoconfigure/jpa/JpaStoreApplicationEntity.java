package org.jfoundry.autoconfigure.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class JpaStoreApplicationEntity {

    @Id
    private String id;
}
