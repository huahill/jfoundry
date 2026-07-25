package org.jfoundry.helidon.integration;

import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("middleware-integration")
@HelidonTest
@AddBean(TransactionPersistenceVerifier.class)
@AddBean(PostgreSqlDataSourceProducer.class)
class PostgreSqlTransactionRunnerTest {

    @Inject
    TransactionPersistenceVerifier verifier;

    @Test
    void commitsThroughTheHelidonTransactionRunnerToPostgreSql() throws Exception {
        assertEquals(1, verifier.persistAndCount());
    }
}
