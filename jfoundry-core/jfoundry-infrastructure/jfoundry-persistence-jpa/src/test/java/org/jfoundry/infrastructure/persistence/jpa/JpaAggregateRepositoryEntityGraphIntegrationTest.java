package org.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.persistence.AggregateEventRegistrar;
import org.jfoundry.infrastructure.persistence.jpa.support.ChildGraphMutationMapper;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrder;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderEntity;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderId;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderLineEntity;
import org.jfoundry.infrastructure.persistence.jpa.support.GraphOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaAggregateRepositoryEntityGraphIntegrationTest {

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> repository;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @BeforeEach
    void beginTransaction() {
        entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        repository = repository(entityManager);
    }

    @AfterEach
    void closeEntityManager() {
        if (entityManager != null) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    @Test
    void addPersistsRootAndChildLines() {
        GraphOrder order = GraphOrder.create(new GraphOrderId("GRAPH-1"), List.of("A", "B"));

        repository.add(order);

        assertThat(entityManager.find(GraphOrderEntity.class, "GRAPH-1")).isNotNull();
        assertThat(lineSkus("GRAPH-1")).containsExactlyInAnyOrder("A", "B");
        assertThat(lineCount("GRAPH-1")).isEqualTo(2);
    }

    @Test
    void modifyReplacesManagedChildGraphAndRemovesOrphans() {
        GraphOrder order = GraphOrder.create(new GraphOrderId("GRAPH-1"), List.of("A", "B"));
        repository.add(order);
        GraphOrder loaded = repository.findById(order.getId());
        loaded.replaceLines(List.of("B", "C"));
        repository.modify(loaded);

        assertThat(lineSkus("GRAPH-1")).containsExactlyInAnyOrder("B", "C");
        assertThat(lineCount("GRAPH-1")).isEqualTo(2);
    }

    @Test
    void modifyAdvancesRootVersion() {
        GraphOrder order = GraphOrder.create(new GraphOrderId("GRAPH-1"), List.of("A", "B"));
        repository.add(order);
        GraphOrder loaded = repository.findById(order.getId());
        GraphOrderEntity managed = entityManager.find(GraphOrderEntity.class, "GRAPH-1");
        long versionBeforeModify = managed.version();

        loaded.replaceLines(List.of("B", "C"));
        repository.modify(loaded);

        assertThat(managed.version()).isGreaterThan(versionBeforeModify);
    }

    @Test
    void addAllPersistsEachGraphAndRegistersEachAggregateOnce() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        repository.setAggregateEventRegistrar(registrar);
        GraphOrder first = GraphOrder.create(new GraphOrderId("GRAPH-BATCH-1"), List.of("A"));
        GraphOrder second = GraphOrder.create(new GraphOrderId("GRAPH-BATCH-2"), List.of("B"));

        repository.addAll(List.of(first, second));

        assertThat(lineSkus("GRAPH-BATCH-1")).containsExactly("A");
        assertThat(lineSkus("GRAPH-BATCH-2")).containsExactly("B");
        assertThat(registrar.registered()).containsExactly(first, second);
    }

    @Test
    void modifyAllUpdatesEachGraphAndRegistersEachAggregateOnce() {
        GraphOrder first = GraphOrder.create(new GraphOrderId("GRAPH-MODIFY-BATCH-1"), List.of("A"));
        GraphOrder second = GraphOrder.create(new GraphOrderId("GRAPH-MODIFY-BATCH-2"), List.of("B"));
        repository.addAll(List.of(first, second));

        RecordingRegistrar registrar = new RecordingRegistrar();
        repository.setAggregateEventRegistrar(registrar);
        GraphOrder loadedFirst = repository.findById(first.getId());
        GraphOrder loadedSecond = repository.findById(second.getId());
        loadedFirst.replaceLines(List.of("A-UPDATED"));
        loadedSecond.replaceLines(List.of("B-UPDATED"));

        repository.modifyAll(List.of(loadedFirst, loadedSecond));

        assertThat(lineSkus("GRAPH-MODIFY-BATCH-1")).containsExactly("A-UPDATED");
        assertThat(lineSkus("GRAPH-MODIFY-BATCH-2")).containsExactly("B-UPDATED");
        assertThat(registrar.registered()).containsExactly(loadedFirst, loadedSecond);
    }

    @Test
    void staleRemoveReportsConflictAndKeepsWinnerGraph() {
        GraphOrderId orderId = new GraphOrderId("GRAPH-REMOVE-CONFLICT");
        repository.add(GraphOrder.create(orderId, List.of("A")));
        entityManager.getTransaction().commit();

        EntityManager staleManager = entityManagerFactory.createEntityManager();
        EntityManager winnerManager = entityManagerFactory.createEntityManager();
        JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> staleRepository =
                repository(staleManager);
        JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> winnerRepository =
                repository(winnerManager);
        staleManager.getTransaction().begin();
        winnerManager.getTransaction().begin();

        try {
            GraphOrder stale = staleRepository.findById(orderId);
            GraphOrder winner = winnerRepository.findById(orderId);
            winner.replaceLines(List.of("B"));
            winnerRepository.modify(winner);
            winnerManager.getTransaction().commit();

            assertThatThrownBy(() -> staleRepository.remove(stale))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("remove optimistic lock conflict")
                    .hasCauseInstanceOf(OptimisticLockException.class);

            staleManager.getTransaction().rollback();
        } finally {
            if (staleManager.getTransaction().isActive()) {
                staleManager.getTransaction().rollback();
            }
            staleManager.close();
            if (winnerManager.getTransaction().isActive()) {
                winnerManager.getTransaction().rollback();
            }
            winnerManager.close();
        }

        EntityManager verificationManager = entityManagerFactory.createEntityManager();
        try {
            GraphOrderEntity winner = verificationManager.find(GraphOrderEntity.class, orderId.value());

            assertThat(winner).isNotNull();
            assertThat(winner.lineSkus()).containsExactly("B");
        } finally {
            verificationManager.close();
        }
    }

    @Test
    void staleChildGraphModifyReportsConflictAndKeepsWinnerGraph() {
        GraphOrderId orderId = new GraphOrderId("GRAPH-MODIFY-CONFLICT");
        repository.add(GraphOrder.create(orderId, List.of("A")));
        entityManager.getTransaction().commit();

        EntityManager staleManager = entityManagerFactory.createEntityManager();
        EntityManager winnerManager = entityManagerFactory.createEntityManager();
        JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> staleRepository =
                repository(staleManager, new ChildGraphMutationMapper());
        JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> winnerRepository =
                repository(winnerManager, new ChildGraphMutationMapper());
        staleManager.getTransaction().begin();
        winnerManager.getTransaction().begin();

        try {
            GraphOrder stale = staleRepository.findById(orderId);
            GraphOrder winner = winnerRepository.findById(orderId);
            winner.replaceLines(List.of("B"));
            winnerRepository.modify(winner);
            winnerManager.getTransaction().commit();

            stale.replaceLines(List.of("C"));

            assertThatThrownBy(() -> staleRepository.modify(stale))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("modify optimistic lock conflict")
                    .hasCauseInstanceOf(OptimisticLockException.class);

            staleManager.getTransaction().rollback();
        } finally {
            if (staleManager.getTransaction().isActive()) {
                staleManager.getTransaction().rollback();
            }
            staleManager.close();
            if (winnerManager.getTransaction().isActive()) {
                winnerManager.getTransaction().rollback();
            }
            winnerManager.close();
        }

        EntityManager verificationManager = entityManagerFactory.createEntityManager();
        try {
            GraphOrderEntity winner = verificationManager.find(GraphOrderEntity.class, orderId.value());

            assertThat(winner).isNotNull();
            assertThat(winner.lineSkus()).containsExactly("B");
        } finally {
            verificationManager.close();
        }
    }

    private JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> repository(
            EntityManager entityManager) {
        return repository(entityManager, new GraphOrderMapper());
    }

    private JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> repository(
            EntityManager entityManager,
            JpaAggregateMapper<GraphOrder, GraphOrderId, GraphOrderEntity, String> mapper) {
        GraphRepository repository = new GraphRepository(entityManager, mapper);
        repository.setAggregatePersistenceContext(new TestPersistenceContext());
        return repository;
    }

    private List<String> lineSkus(String orderId) {
        return entityManager.createQuery(
                        "select line.sku from GraphOrderLineEntity line where line.order.id = :orderId",
                        String.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    private long lineCount(String orderId) {
        return entityManager.createQuery(
                        "select count(line) from GraphOrderLineEntity line where line.order.id = :orderId",
                        Long.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private static final class GraphRepository extends
            JpaAggregateRepository<GraphOrder, GraphOrderId, GraphOrderEntity, String> {

        private GraphRepository(
                EntityManager entityManager,
                JpaAggregateMapper<GraphOrder, GraphOrderId, GraphOrderEntity, String> mapper) {
            super(entityManager, GraphOrderEntity.class, mapper);
        }
    }

    private static final class RecordingRegistrar implements AggregateEventRegistrar {

        private final List<EventRecordable> registered = new ArrayList<>();

        @Override
        public void register(EventRecordable aggregate) {
            registered.add(aggregate);
        }

        private List<EventRecordable> registered() {
            return List.copyOf(registered);
        }
    }
}
