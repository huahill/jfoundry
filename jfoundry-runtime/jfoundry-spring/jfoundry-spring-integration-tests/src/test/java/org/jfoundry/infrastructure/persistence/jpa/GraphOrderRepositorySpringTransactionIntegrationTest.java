package org.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrder;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderEntity;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderId;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = GraphOrderRepositorySpringTransactionIntegrationTest.TestApplication.class,
        properties = {
                "spring.datasource.generate-unique-name=true",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false"
        })
@Transactional
class GraphOrderRepositorySpringTransactionIntegrationTest {

    @Autowired
    private GraphOrderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void usesTransactionBoundPersistenceContextInjectedByAutoConfiguration() {
        GraphOrderId id = new GraphOrderId("SPRING-GRAPH-1");
        repository.add(GraphOrder.create(id, List.of("A", "B")));
        GraphOrder loaded = repository.findById(id);
        loaded.replaceLines(List.of("B", "C"));

        repository.modify(loaded);

        GraphOrderEntity entity = entityManager.find(GraphOrderEntity.class, id.value());
        assertThat(entity.lineSkus()).containsExactlyInAnyOrder("B", "C");
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = GraphOrderEntity.class)
    static class TestApplication {

        @Bean
        GraphOrderRepository graphOrderRepository(EntityManager entityManager) {
            return new GraphOrderRepository(entityManager, new GraphOrderMapper());
        }
    }

    static final class GraphOrderRepository extends
            JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> {

        private GraphOrderRepository(EntityManager entityManager, GraphOrderMapper mapper) {
            super(entityManager, GraphOrderEntity.class, mapper);
        }
    }
}
