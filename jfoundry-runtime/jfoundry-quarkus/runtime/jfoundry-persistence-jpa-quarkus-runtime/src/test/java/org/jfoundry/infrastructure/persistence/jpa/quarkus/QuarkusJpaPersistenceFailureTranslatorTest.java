package org.jfoundry.infrastructure.persistence.jpa.quarkus;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.QueryTimeoutException;
import org.hibernate.exception.JDBCConnectionException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.infrastructure.persistence.PersistenceOperation;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransientConnectionException;
import java.sql.SQLTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusJpaPersistenceFailureTranslatorTest {

    private final QuarkusJpaPersistenceFailureTranslator translator =
            new QuarkusJpaPersistenceFailureTranslator();

    @Test
    void translatesHibernateConnectionFailures() {
        JDBCConnectionException failure = new JDBCConnectionException(
                "database unavailable", new SQLTransientConnectionException("offline"));

        RuntimeException translated = translator.translate(PersistenceOperation.QUERY, failure);

        assertThat(translated).isInstanceOf(ExternalAccessException.class)
                .hasMessage("Persistence query failed");
        assertThat(translated.getCause()).isSameAs(failure);
    }

    @Test
    void translatesHibernateQueryTimeouts() {
        QueryTimeoutException failure = new QueryTimeoutException(
                "query timed out", new SQLTimeoutException("timed out"), "select 1");

        RuntimeException translated = translator.translate(PersistenceOperation.FIND, failure);

        assertThat(translated).isInstanceOf(ExternalAccessException.class)
                .hasMessage("Persistence find failed");
        assertThat(translated.getCause()).isSameAs(failure);
    }

    @Test
    void preservesOptimisticLockFailures() {
        OptimisticLockException failure = new OptimisticLockException("conflict");

        assertThat(translator.translate(PersistenceOperation.MODIFY, failure)).isSameAs(failure);
    }

    @Test
    void preservesUnrelatedPersistenceFailures() {
        IllegalStateException failure = new IllegalStateException("unexpected");

        assertThat(translator.translate(PersistenceOperation.ADD, failure)).isSameAs(failure);
    }
}
