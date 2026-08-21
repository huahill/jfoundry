package org.jfoundry.infrastructure.persistence.mybatis.support;

import org.jmolecules.ddd.types.Identifier;

import java.io.Serializable;

/// Test aggregate root ID using a strongly typed Identifier.
public record TestOrderId(String value) implements Identifier, Serializable {

    @Override
    public String toString() {
        return value;
    }
}
