package org.jfoundry.quarkus.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("middleware-integration")
@QuarkusTest
@QuarkusTestResource(PostgreSqlTestResource.class)
class PostgreSqlOutboxJpaResourceTest {

    @Inject
    DataSource dataSource;

    @Test
    void persistsOutboxMessagesThroughTheQuarkusTransactionRunnerAgainstPostgreSql() throws Exception {
        assertEquals("PostgreSQL", databaseProductName());
        assertEquals(0, outboxMessageCount());

        when()
                .get("/jfoundry/outbox")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("PENDING"));

        assertEquals(1, outboxMessageCount());
    }

    private String databaseProductName() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        }
    }

    private int outboxMessageCount() throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("select count(*) from jfoundry_outbox_event")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
