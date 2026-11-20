package io.huahill.jfoundry.infrastructure.persistence.mybatis;

import java.io.Serializable;
import java.util.List;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

/// Maps a domain aggregate to the MyBatis-Plus row that is inserted or updated.
///
/// This contract belongs to the MyBatis-Plus adapter. JPA aggregates use
/// {@code JpaAggregateMapper}, which applies state to the managed entity instead of producing a
/// new row object.
///
/// @param <T> aggregate root type
/// @param <ID> domain identifier type
/// @param <D> MyBatis-Plus row type
/// @param <K> persistence primary-key type
public interface MybatisPlusDataMapper<
        T extends AggregateRoot<T, ID>,
        ID extends Identifier,
        D extends MybatisPlusAggregateData<K>,
        K extends Serializable> {

    /// Maps an aggregate root to a new row object.
    D toData(T aggregate);

    /// Maps a domain identifier to its persistence identifier.
    K toDataId(ID id);

    /// Restores an aggregate root from a row object.
    T toEntity(D data);

    /// Maps aggregate roots to row objects.
    default List<D> toDataList(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(this::toData).toList();
    }

    /// Restores aggregate roots from row objects.
    default List<T> toEntityList(List<D> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return List.of();
        }
        return dataList.stream().map(this::toEntity).toList();
    }
}
