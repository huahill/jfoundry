package io.huahill.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import io.huahill.jfoundry.application.exception.ConflictException;
import io.huahill.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import io.huahill.jfoundry.infrastructure.persistence.jpa.support.TestOrder;
import io.huahill.jfoundry.infrastructure.persistence.jpa.support.TestOrderEntity;
import io.huahill.jfoundry.infrastructure.persistence.jpa.support.TestOrderId;
import io.huahill.jfoundry.infrastructure.persistence.jpa.support.TestOrderMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaAggregateRepositoryIntegrationTest {

    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @Test
    void persistenceContextAwarenessShouldRemainProxyable() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(JpaAggregateRepository.class
                .getMethod("setAggregatePersistenceContext", AggregatePersistenceContext.class)
                .getModifiers())).isFalse();
    }

    @Test
    void persistsLoadsAndUpdatesTheManagedEntity() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TestOrderMapper mapper = new TestOrderMapper();
        TestRepository repository = repository(entityManager, mapper);
        entityManager.getTransaction().begin();

        TestOrder created = TestOrder.create("JPA-ONE");
        repository.add(created);
        TestOrder loaded = repository.findById(created.getId());
        TestOrderEntity managed = entityManager.find(TestOrderEntity.class, "JPA-ONE");
        loaded.markPaid();
        repository.modify(loaded);

        assertThat(mapper.lastAppliedEntity()).isSameAs(managed);
        assertThat(managed.status()).isEqualTo("PAID");
        assertThat(managed.version()).isEqualTo(1L);
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    @Test
    void staleManagedEntityThrowsConflictWithProviderCause() {
        seed("JPA-CONFLICT");
        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        TestRepository firstRepository = repository(firstManager, new TestOrderMapper());
        TestRepository secondRepository = repository(secondManager, new TestOrderMapper());
        firstManager.getTransaction().begin();
        secondManager.getTransaction().begin();

        TestOrder stale = firstRepository.findById(new TestOrderId("JPA-CONFLICT"));
        TestOrder winner = secondRepository.findById(new TestOrderId("JPA-CONFLICT"));
        winner.markPaid();
        secondRepository.modify(winner);
        secondManager.getTransaction().commit();
        winner = null;
        stale.cancel();

        assertThatThrownBy(() -> firstRepository.modify(stale))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("optimistic lock conflict")
                .hasCauseInstanceOf(OptimisticLockException.class);
        firstManager.getTransaction().rollback();
        firstManager.close();
        secondManager.close();
    }

    @Test
    void removesTrackedManagedEntity() {
        seed("JPA-REMOVE");
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TestRepository repository = repository(entityManager, new TestOrderMapper());
        entityManager.getTransaction().begin();

        TestOrder loaded = repository.findById(new TestOrderId("JPA-REMOVE"));
        repository.remove(loaded);

        assertThat(entityManager.find(TestOrderEntity.class, "JPA-REMOVE")).isNull();
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    @Test
    void rejectsUntrackedAggregateInsteadOfMergingIt() {
        seed("JPA-UNTRACKED");
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TestRepository repository = repository(entityManager, new TestOrderMapper());
        entityManager.getTransaction().begin();
        TestOrder detachedShape = TestOrder.restore(new TestOrderId("JPA-UNTRACKED"), "PAID");

        assertThatThrownBy(() -> repository.modify(detachedShape))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not tracked");

        entityManager.getTransaction().rollback();
        entityManager.close();
    }

    @Test
    void failsClearlyWhenPersistenceContextWasNotInjected() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TestRepository repository = new TestRepository(entityManager, new TestOrderMapper());
        entityManager.getTransaction().begin();

        assertThatThrownBy(() -> repository.add(TestOrder.create("JPA-NO-CONTEXT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AggregatePersistenceContext");

        entityManager.getTransaction().rollback();
        entityManager.close();
    }

    private static void seed(String id) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.persist(new TestOrderEntity(id, "CREATED"));
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    private static TestRepository repository(EntityManager entityManager, TestOrderMapper mapper) {
        TestRepository repository = new TestRepository(entityManager, mapper);
        repository.setAggregatePersistenceContext(new TestPersistenceContext());
        return repository;
    }

    private static final class TestRepository extends
            JpaAggregateRepository<TestOrder, TestOrderId, TestOrderEntity, String> {

        private TestRepository(
                EntityManager entityManager,
                TestOrderMapper mapper) {
            super(entityManager, TestOrderEntity.class, mapper);
        }
    }
}
