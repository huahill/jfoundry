package io.huahill.jfoundry.infrastructure.persistence;

/// Persistence operation being performed when a technical failure occurred.
public enum PersistenceOperation {
    FIND,
    ADD,
    MODIFY,
    REMOVE,
    QUERY
}
