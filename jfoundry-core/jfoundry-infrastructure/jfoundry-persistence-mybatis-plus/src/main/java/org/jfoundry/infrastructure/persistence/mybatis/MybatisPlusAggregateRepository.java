package org.jfoundry.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.jfoundry.infrastructure.persistence.AggregateData;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;
import org.jfoundry.infrastructure.persistence.DataMapper;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// MyBatis-Plus persistence base for an aggregate whose root is represented by one data object.
/// <p>
/// The default implementation stores a single data object. Adapters for multi-table aggregates may
/// override the complete {@code do*} operations and use the protected operation methods to retain
/// root persistence, optional optimistic locking, and persistence-context tracking.
///
/// @param <T> aggregate root type
/// @param <ID> aggregate identifier type
/// @param <D> persistence data type
/// @param <K> persistence identifier type
public abstract class MybatisPlusAggregateRepository<
        T extends AggregateRoot<T, ID>,
        ID extends Identifier,
        D extends AggregateData<K>,
        K extends Serializable>
        extends AbstractAggregateRepository<T, ID>
        implements AggregatePersistenceContextAware {

    protected final BaseMapper<D> mapper;
    private final Function<T, D> toRootData;
    private final Function<ID, K> toDataId;
    private final DataMapper<T, ID, D, K> dataMapper;
    private final Class<D> dataType;
    private final PersistenceStateKey<VersionState> versionKey;
    private AggregatePersistenceContext persistenceContext;

    protected MybatisPlusAggregateRepository(
            BaseMapper<D> mapper,
            DataMapper<T, ID, D, K> dataMapper) {
        this(mapper, dataMapper::toData, dataMapper::toDataId, dataMapper, null);
    }

    protected MybatisPlusAggregateRepository(
            BaseMapper<D> mapper,
            DataMapper<T, ID, D, K> dataMapper,
            Class<D> dataType) {
        this(mapper, dataMapper::toData, dataMapper::toDataId, dataMapper, dataType);
    }

    protected MybatisPlusAggregateRepository(
            BaseMapper<D> mapper,
            Function<T, D> toRootData,
            Function<ID, K> toDataId,
            Class<D> dataType) {
        this(mapper, toRootData, toDataId, null, dataType);
    }

    private MybatisPlusAggregateRepository(
            BaseMapper<D> mapper,
            Function<T, D> toRootData,
            Function<ID, K> toDataId,
            DataMapper<T, ID, D, K> dataMapper,
            Class<D> dataType) {
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null.");
        this.toRootData = Objects.requireNonNull(
                toRootData, "Root data mapping function must not be null.");
        this.toDataId = Objects.requireNonNull(
                toDataId, "Data identifier mapping function must not be null.");
        this.dataMapper = dataMapper;
        this.dataType = dataType;
        this.versionKey = PersistenceStateKey.of(
                (dataType == null ? getClass().getName() : dataType.getName()) + ".version",
                VersionState.class);
    }

    /// Bidirectional data mapper available to single-data subclasses.
    protected final DataMapper<T, ID, D, K> dataMapper() {
        if (dataMapper == null) {
            throw new IllegalStateException(
                    "This repository does not define reverse data mapping.");
        }
        return dataMapper;
    }

    @Override
    public void setAggregatePersistenceContext(
            AggregatePersistenceContext persistenceContext) {
        this.persistenceContext = Objects.requireNonNull(
                persistenceContext, "AggregatePersistenceContext must not be null.");
    }

    @Override
    protected T doFindById(ID id) {
        return loadAggregate(id, dataMapper()::toEntity);
    }

    /// Loads the root data, restores the complete aggregate, and then tracks persistence state.
    protected final T loadAggregate(ID id, Function<D, T> restorer) {
        Objects.requireNonNull(restorer, "Aggregate restorer must not be null.");
        D data = mapper.selectById(toDataId.apply(id));
        if (data == null) {
            return null;
        }
        T aggregate = Objects.requireNonNull(
                restorer.apply(data), "Aggregate restorer must not return null.");
        if (isVersioned()) {
            requirePersistenceContext().attach(aggregate, versionKey, versionState(data));
        }
        return aggregate;
    }

    @Override
    protected void doAdd(T aggregate) {
        insertAggregate(aggregate, toRootData.apply(aggregate), ignored -> { });
    }

    /// Inserts the root data and additional aggregate state before tracking persistence state.
    protected final void insertAggregate(
            T aggregate,
            D rootData,
            Consumer<D> completeInsert) {
        insertAggregate(aggregate, rootData, completeInsert, Function.identity());
    }

    /// Inserts the root with root-specific failure translation, completes additional writes, and
    /// then tracks persistence state.
    protected final void insertAggregate(
            T aggregate,
            D rootData,
            Consumer<D> completeInsert,
            Function<RuntimeException, RuntimeException> rootFailureTranslator) {
        Objects.requireNonNull(rootData, "Root data must not be null.");
        Objects.requireNonNull(completeInsert, "Complete insert callback must not be null.");
        Objects.requireNonNull(
                rootFailureTranslator, "Root failure translator must not be null.");
        try {
            mapper.insert(rootData);
        } catch (RuntimeException failure) {
            throw Objects.requireNonNull(
                    rootFailureTranslator.apply(failure),
                    "Root failure translator must not return null.");
        }
        completeInsert.accept(rootData);
        if (isVersioned()) {
            requirePersistenceContext().attach(aggregate, versionKey, versionState(rootData));
        }
    }

    @Override
    protected void doModify(T aggregate) {
        updateAggregate(aggregate, toRootData.apply(aggregate), ignored -> { });
    }

    /// Updates the root data, completes additional writes, and then advances tracked state.
    protected final void updateAggregate(
            T aggregate,
            D rootData,
            Consumer<D> completeUpdate) {
        Objects.requireNonNull(rootData, "Root data must not be null.");
        Objects.requireNonNull(completeUpdate, "Complete update callback must not be null.");
        if (!isVersioned()) {
            long count = mapper.updateById(rootData);
            requireAffectedRow(count, "modify affected 0 rows - aggregate not found: "
                    + aggregate.getId());
            completeUpdate.accept(rootData);
            return;
        }

        VersionState original = requirePersistenceContext().require(aggregate, versionKey);
        writeVersion(rootData, original.value());
        long count = mapper.updateById(rootData);
        requireVersionedRow(count, "modify", aggregate);
        VersionState current = versionState(rootData);
        if (Objects.equals(original.value(), current.value())) {
            throw new IllegalStateException(
                    "Versioned persistence data was not advanced; configure "
                            + "OptimisticLockerInnerInterceptor: " + dataType.getName());
        }
        completeUpdate.accept(rootData);
        requirePersistenceContext().replace(aggregate, versionKey, current);
    }

    @Override
    protected void doRemove(T aggregate) {
        deleteAggregate(aggregate, () -> { });
    }

    /// Deletes the root data before completing any additional removal work.
    /// <p>
    /// Root-first deletion makes optimistic-lock conflicts observable before additional writes.
    /// Composite adapters should use database cascades when dependent rows require parent-first
    /// deletion ordering.
    protected final void deleteAggregate(T aggregate, Runnable completeRemoval) {
        Objects.requireNonNull(completeRemoval, "Complete removal callback must not be null.");
        if (!isVersioned()) {
            long count = mapper.deleteById(toDataId.apply(aggregate.getId()));
            requireAffectedRow(count, "remove affected 0 rows - aggregate not found: "
                    + aggregate.getId());
            completeRemoval.run();
            return;
        }

        VersionState original = requirePersistenceContext().require(aggregate, versionKey);
        TableInfo tableInfo = requireTableInfo();
        QueryWrapper<D> wrapper = new QueryWrapper<>();
        wrapper.eq(tableInfo.getKeyColumn(), toDataId.apply(aggregate.getId()));
        wrapper.eq(tableInfo.getVersionFieldInfo().getColumn(), original.value());
        long count = mapper.delete(wrapper);
        requireVersionedRow(count, "remove", aggregate);
        completeRemoval.run();
    }

    private boolean isVersioned() {
        return dataType != null && requireTableInfo().isWithVersion();
    }

    private TableInfo requireTableInfo() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(dataType);
        if (tableInfo == null || !tableInfo.havePK()) {
            throw new IllegalStateException(
                    "Persistence data must declare a MyBatis-Plus table and primary key: "
                            + dataType.getName());
        }
        return tableInfo;
    }

    private VersionState versionState(D data) {
        Object version = readVersion(data);
        if (version == null) {
            throw new IllegalStateException(
                    "Versioned persistence data must provide a version: " + dataType.getName());
        }
        return new VersionState(version);
    }

    private Object readVersion(D data) {
        return SystemMetaObject.forObject(data).getValue(versionField().getProperty());
    }

    private void writeVersion(D data, Object version) {
        SystemMetaObject.forObject(data).setValue(versionField().getProperty(), version);
    }

    private TableFieldInfo versionField() {
        TableFieldInfo versionField = requireTableInfo().getVersionFieldInfo();
        if (versionField == null) {
            throw new IllegalStateException(
                    "Persistence data does not declare a @Version field: " + dataType.getName());
        }
        return versionField;
    }

    private AggregatePersistenceContext requirePersistenceContext() {
        if (persistenceContext == null) {
            throw new IllegalStateException(
                    "Versioned aggregate persistence requires an AggregatePersistenceContext.");
        }
        return persistenceContext;
    }

    private static void requireAffectedRow(long count, String message) {
        if (count == 0) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireVersionedRow(long count, String operation, Object aggregate) {
        if (count == 0) {
            throw new ConflictException(
                    operation + " optimistic lock conflict for aggregate: " + aggregate);
        }
    }

    private record VersionState(Object value) {
    }
}
