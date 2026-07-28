package org.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.jfoundry.infrastructure.persistence.jpa.support.TestOrder;
import org.jfoundry.infrastructure.persistence.jpa.support.TestOrderId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JpaAuditStampingTest {

    private static final Instant NOW = Instant.parse("2026-07-28T09:30:00Z");

    @Test
    void stampsAnAuditDataSnapshotForPersistenceAndUpdate() {
        JpaAuditStamping auditStamping = new JpaAuditStamping(new AuditStamping(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> Optional.of("operator-42")));
        TestAuditData data = new TestAuditData();

        auditStamping.stampForPersist(data);

        assertThat(data.getCreatedAt()).isEqualTo(NOW);
        assertThat(data.getCreatedBy()).isEqualTo("operator-42");
        assertThat(data.getLastModifiedAt()).isEqualTo(NOW);
        assertThat(data.getLastModifiedBy()).isEqualTo("operator-42");

        auditStamping.stampForUpdate(data);

        assertThat(data.getCreatedAt()).isEqualTo(NOW);
        assertThat(data.getCreatedBy()).isEqualTo("operator-42");
        assertThat(data.getLastModifiedAt()).isEqualTo(NOW);
        assertThat(data.getLastModifiedBy()).isEqualTo("operator-42");
    }

    @Test
    void repositoryStampsAuditDataBeforePersistingIt() {
        EntityManager entityManager = mock(EntityManager.class);
        AuditedOrderMapper mapper = new AuditedOrderMapper();
        AuditStamping auditStamping = new AuditStamping(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> Optional.of("operator-42"));
        AuditedRepository repository = new AuditedRepository(entityManager, mapper,
                new JpaAuditStamping(auditStamping));
        repository.setAggregatePersistenceContext(new AggregatePersistenceContext() {
            @Override
            public <S> void attach(Object aggregate, org.jfoundry.infrastructure.persistence.PersistenceStateKey<S> key,
                                   S state) {
            }

            @Override
            public <S> S require(Object aggregate, org.jfoundry.infrastructure.persistence.PersistenceStateKey<S> key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> void replace(Object aggregate, org.jfoundry.infrastructure.persistence.PersistenceStateKey<S> key,
                                    S state) {
                throw new UnsupportedOperationException();
            }
        });

        repository.add(TestOrder.create("AUDIT-ONE"));

        verify(entityManager).persist(any(AuditedOrderEntity.class));
        assertThat(mapper.createdEntity.getCreatedAt()).isEqualTo(NOW);
        assertThat(mapper.createdEntity.getCreatedBy()).isEqualTo("operator-42");
        assertThat(mapper.createdEntity.getLastModifiedAt()).isEqualTo(NOW);
        assertThat(mapper.createdEntity.getLastModifiedBy()).isEqualTo("operator-42");
    }

    private static final class TestAuditData extends JpaAuditData {
    }

    private static final class AuditedRepository extends
            JpaAggregateRepository<TestOrder, TestOrderId, AuditedOrderEntity, String> {

        private AuditedRepository(EntityManager entityManager, AuditedOrderMapper mapper,
                                  JpaAuditStamping auditStamping) {
            super(entityManager, AuditedOrderEntity.class, mapper, auditStamping);
        }
    }

    private static final class AuditedOrderEntity extends JpaAuditData {
        private final String id;

        private AuditedOrderEntity(String id) {
            this.id = id;
        }
    }

    private static final class AuditedOrderMapper
            implements JpaAggregateMapper<TestOrder, TestOrderId, AuditedOrderEntity, String> {

        private AuditedOrderEntity createdEntity;

        @Override
        public String toEntityId(TestOrderId id) {
            return id.value();
        }

        @Override
        public AuditedOrderEntity newEntity(TestOrder aggregate) {
            createdEntity = new AuditedOrderEntity(aggregate.getId().value());
            return createdEntity;
        }

        @Override
        public TestOrder toAggregate(AuditedOrderEntity entity) {
            return TestOrder.restore(new TestOrderId(entity.id), "CREATED");
        }

        @Override
        public void apply(TestOrder aggregate, AuditedOrderEntity entity) {
        }
    }
}
