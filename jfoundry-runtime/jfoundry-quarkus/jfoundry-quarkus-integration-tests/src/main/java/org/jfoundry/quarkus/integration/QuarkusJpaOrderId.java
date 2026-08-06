package org.jfoundry.quarkus.integration;

import org.jmolecules.ddd.types.Identifier;

import java.io.Serializable;

record QuarkusJpaOrderId(String value) implements Identifier, Serializable {
}
