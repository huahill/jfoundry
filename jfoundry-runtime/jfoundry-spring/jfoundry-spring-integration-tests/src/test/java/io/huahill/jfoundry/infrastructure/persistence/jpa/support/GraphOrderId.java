package io.huahill.jfoundry.infrastructure.persistence.jpa.support;

import org.jmolecules.ddd.types.Identifier;

import java.io.Serializable;

public record GraphOrderId(String value) implements Identifier, Serializable {
}
