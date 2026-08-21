package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlIntegrationTestProfileTest {

    @Test
    void suppliesPostgreSqlToEveryNativeIntegrationTest() throws NoSuchMethodException {
        assertTrue(PostgreSqlIntegrationTestProfile.class.getConstructor().canAccess(null));
        var testResource = new PostgreSqlIntegrationTestProfile().testResources().getFirst();

        assertEquals(PostgreSqlTestResource.class, testResource.getClazz());
        assertEquals("postgresql", new PostgreSqlIntegrationTestProfile().getConfigOverrides()
                .get("quarkus.datasource.db-kind"));
        assertEquals(List.of(PostgreSqlIntegrationTestProfile.class), List.of(
                DomainEventDispatchResourceIT.class,
                DomainEventExternalizationResourceIT.class,
                InboxJpaResourceIT.class,
                JpaAggregateRepositoryResourceIT.class,
                OutboxDispatchResourceIT.class,
                OutboxJpaResourceIT.class,
                OutboxMaintenanceResourceIT.class,
                ProblemDetailsResourceIT.class,
                TransactionRunnerResourceIT.class
        ).stream().map(PostgreSqlIntegrationTestProfileTest::profileOf).distinct().toList());
    }

    private static Class<? extends QuarkusTestProfile> profileOf(Class<?> testClass) {
        return testClass.getAnnotation(TestProfile.class).value();
    }
}
