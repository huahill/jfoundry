package io.huahill.jfoundry.infrastructure.persistence;

/// Translates runtime-specific persistence failures without coupling persistence core to a runtime.
@FunctionalInterface
public interface PersistenceFailureTranslator {

    /// Returns the exception that should cross the persistence boundary.
    RuntimeException translate(PersistenceOperation operation, RuntimeException failure);

    /// Returns a translator that preserves the original failure.
    static PersistenceFailureTranslator passThrough() {
        return (operation, failure) -> failure;
    }
}
