package org.jfoundry.infrastructure.persistence.jpa.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.persistence.QueryTimeoutException;
import org.hibernate.exception.JDBCConnectionException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;
import org.jfoundry.infrastructure.persistence.PersistenceOperation;

import java.util.Locale;
import java.util.Objects;

/// Translates Hibernate availability failures at a Helidon JPA persistence boundary.
@Dependent
public final class HelidonJpaPersistenceFailureTranslator implements PersistenceFailureTranslator {

    @Override
    public RuntimeException translate(PersistenceOperation operation, RuntimeException failure) {
        Objects.requireNonNull(operation, "Persistence operation must not be null");
        Objects.requireNonNull(failure, "Persistence failure must not be null");
        if (failure instanceof JDBCConnectionException
                || failure instanceof org.hibernate.QueryTimeoutException
                || failure instanceof QueryTimeoutException) {
            return new ExternalAccessException(
                    "Persistence " + operation.name().toLowerCase(Locale.ROOT) + " failed", failure);
        }
        return failure;
    }
}
