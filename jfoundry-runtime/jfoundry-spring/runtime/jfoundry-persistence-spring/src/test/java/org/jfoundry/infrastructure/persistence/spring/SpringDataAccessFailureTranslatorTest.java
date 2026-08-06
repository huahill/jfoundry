package org.jfoundry.infrastructure.persistence.spring;

import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.infrastructure.persistence.PersistenceOperation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDataAccessFailureTranslatorTest {

    private final SpringDataAccessFailureTranslator translator =
            new SpringDataAccessFailureTranslator();

    @Test
    void translatesKnownAvailabilityFailuresAndPreservesTheirCause() {
        List<RuntimeException> failures = List.of(
                new DataAccessResourceFailureException("database unavailable"),
                new TransientDataAccessResourceException("database restarting"),
                new QueryTimeoutException("query timed out"));

        for (RuntimeException failure : failures) {
            RuntimeException translated = translator.translate(PersistenceOperation.QUERY, failure);

            assertThat(translated)
                    .isInstanceOf(ExternalAccessException.class)
                    .hasMessage("Persistence query failed");
            assertThat(translated).cause().isSameAs(failure);
        }
    }

    @Test
    void preservesFailuresWhoseMeaningRequiresBusinessOrProgrammingContext() {
        List<RuntimeException> failures = List.of(
                new DuplicateKeyException("duplicate"),
                new OptimisticLockingFailureException("stale"),
                new BadSqlGrammarException("query", "select broken", new SQLException("syntax")),
                new IllegalStateException("unexpected"));

        for (RuntimeException failure : failures) {
            assertThat(translator.translate(PersistenceOperation.ADD, failure)).isSameAs(failure);
        }
    }
}
