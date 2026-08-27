package org.jfoundry.infrastructure.persistence.jpa.helidon;

import jakarta.enterprise.context.Dependent;
import org.hibernate.exception.JDBCConnectionException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.infrastructure.persistence.PersistenceOperation;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonJpaPersistenceFailureTranslatorTest {

    @Test
    void usesDependentScopeForNativeCdiCompatibility() {
        assertThat(HelidonJpaPersistenceFailureTranslator.class.isAnnotationPresent(Dependent.class)).isTrue();
    }

    @Test
    void translatesHibernateConnectionFailures() {
        JDBCConnectionException failure = new JDBCConnectionException("offline", new SQLTransientConnectionException("offline"));
        RuntimeException translated = new HelidonJpaPersistenceFailureTranslator().translate(PersistenceOperation.QUERY, failure);
        assertThat(translated).isInstanceOf(ExternalAccessException.class).hasMessage("Persistence query failed");
    }
}
