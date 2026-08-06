package org.jfoundry.infrastructure.persistence.spring;

import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;
import org.jfoundry.infrastructure.persistence.PersistenceOperation;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.Locale;
import java.util.Objects;

/// Translates known Spring data-access availability failures at a persistence boundary.
public final class SpringDataAccessFailureTranslator implements PersistenceFailureTranslator {

    @Override
    public RuntimeException translate(PersistenceOperation operation, RuntimeException failure) {
        Objects.requireNonNull(operation, "PersistenceOperation must not be null.");
        Objects.requireNonNull(failure, "Persistence failure must not be null.");
        if (failure instanceof DataAccessResourceFailureException
                || failure instanceof TransientDataAccessResourceException
                || failure instanceof QueryTimeoutException) {
            return new ExternalAccessException(
                    "Persistence " + operation.name().toLowerCase(Locale.ROOT) + " failed",
                    failure);
        }
        return failure;
    }
}
