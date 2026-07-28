package org.jfoundry.infrastructure.persistence.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

import java.util.Objects;

/// Jakarta Persistence repository base for aggregates represented by one entity graph.
/// <p>
/// Loaded managed entities are tracked outside the domain aggregate. Modification applies state
/// to that same entity and never calls {@link EntityManager#merge(Object)}. The application must
/// keep load and modification in one transaction and persistence context.
public abstract class JpaAggregateRepository<
        A extends AggregateRoot<A, ID> & EventRecordable,
        ID extends Identifier,
        E,
        K>
        extends AbstractAggregateRepository<A, ID>
        implements AggregatePersistenceContextAware {

    protected final EntityManager entityManager;
    private final Class<E> entityType;
    private final JpaAggregateMapper<A, ID, E, K> mapper;
    private final JpaAuditStamping auditStamping;
    private AggregatePersistenceContext persistenceContext;
    private final PersistenceStateKey<E> managedEntityKey;

    protected JpaAggregateRepository(
            EntityManager entityManager,
            Class<E> entityType,
            JpaAggregateMapper<A, ID, E, K> mapper) {
        this(entityManager, entityType, mapper, null);
    }

    /// Creates a repository that applies technical audit stamps to {@link JpaAuditData} entities.
    protected JpaAggregateRepository(
            EntityManager entityManager,
            Class<E> entityType,
            JpaAggregateMapper<A, ID, E, K> mapper,
            JpaAuditStamping auditStamping) {
        this.entityManager = Objects.requireNonNull(entityManager, "EntityManager must not be null.");
        this.entityType = Objects.requireNonNull(entityType, "Entity type must not be null.");
        this.mapper = Objects.requireNonNull(mapper, "JpaAggregateMapper must not be null.");
        this.auditStamping = auditStamping;
        this.managedEntityKey = PersistenceStateKey.of(
                entityType.getName() + ".managed-entity", entityType);
    }

    @Override
    public void setAggregatePersistenceContext(
            AggregatePersistenceContext persistenceContext) {
        this.persistenceContext = Objects.requireNonNull(
                persistenceContext, "AggregatePersistenceContext must not be null.");
    }

    protected final JpaAggregateMapper<A, ID, E, K> mapper() {
        return mapper;
    }

    protected final AggregatePersistenceContext persistenceContext() {
        return requirePersistenceContext();
    }

    @Override
    protected final A doFindById(ID id) {
        AggregatePersistenceContext context = requirePersistenceContext();
        E entity = entityManager.find(entityType, mapper.toEntityId(id));
        if (entity == null) {
            return null;
        }
        A aggregate = mapper.toAggregate(entity);
        context.attach(aggregate, managedEntityKey, entity);
        return aggregate;
    }

    @Override
    protected final void doAdd(A aggregate) {
        AggregatePersistenceContext context = requirePersistenceContext();
        E entity = mapper.newEntity(aggregate);
        stampForPersist(entity);
        entityManager.persist(entity);
        flush("add", aggregate);
        context.attach(aggregate, managedEntityKey, entity);
    }

    @Override
    protected final void doModify(A aggregate) {
        E managedEntity = requirePersistenceContext().require(aggregate, managedEntityKey);
        mapper.apply(aggregate, managedEntity);
        stampForUpdate(managedEntity);
        flush("modify", aggregate);
    }

    @Override
    protected final void doRemove(A aggregate) {
        E managedEntity = requirePersistenceContext().require(aggregate, managedEntityKey);
        entityManager.remove(managedEntity);
        flush("remove", aggregate);
    }

    private void flush(String operation, A aggregate) {
        try {
            entityManager.flush();
        } catch (OptimisticLockException failure) {
            throw new ConflictException(
                    operation + " optimistic lock conflict for aggregate: " + aggregate.getId(),
                    failure);
        }
    }

    private void stampForPersist(E entity) {
        if (auditStamping != null && entity instanceof JpaAuditData auditData) {
            auditStamping.stampForPersist(auditData);
        }
    }

    private void stampForUpdate(E entity) {
        if (auditStamping != null && entity instanceof JpaAuditData auditData) {
            auditStamping.stampForUpdate(auditData);
        }
    }

    private AggregatePersistenceContext requirePersistenceContext() {
        if (persistenceContext == null) {
            throw new IllegalStateException(
                    "JPA aggregate persistence requires an AggregatePersistenceContext.");
        }
        return persistenceContext;
    }
}
