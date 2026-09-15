package org.jfoundry.infrastructure.persistence;

import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.TypeVariable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMapperTest {

    private final DataMapper<TestEntity, TestId, TestData, String> dataMapper = new DataMapper<>() {
        @Override
        public TestData toData(TestEntity entity) {
            TestData data = new TestData();
            data.setId(toDataId(entity.getId()));
            data.name = entity.name;
            return data;
        }

        @Override
        public TestEntity toEntity(TestData data) {
            return new TestEntity(new TestId(data.getId()), data.name);
        }

        @Override
        public String toDataId(TestId id) {
            return id == null ? null : id.value();
        }
    };

    @Test
    void shouldConvertEntityCollectionToDataListByDefault() {
        List<TestData> dataList = dataMapper.toDataList(List.of(
                new TestEntity(new TestId("1"), "created"),
                new TestEntity(new TestId("2"), "updated")
        ));

        assertEquals(List.of("1", "2"), dataList.stream().map(TestData::getId).toList());
        assertEquals(List.of("created", "updated"), dataList.stream().map(data -> data.name).toList());
    }

    @Test
    void shouldConvertDataCollectionToEntityListByDefault() {
        TestData first = new TestData();
        first.setId("1");
        first.name = "created";
        TestData second = new TestData();
        second.setId("2");
        second.name = "updated";

        List<TestEntity> entities = dataMapper.toEntityList(List.of(first, second));

        assertEquals(List.of("1", "2"), entities.stream().map(e -> e.getId().value()).toList());
        assertEquals(List.of("created", "updated"), entities.stream().map(e -> e.name).toList());
    }

    @Test
    void shouldTreatNullAndEmptyCollectionsAsEmptyLists() {
        assertTrue(dataMapper.toDataList(null).isEmpty());
        assertTrue(dataMapper.toDataList(List.of()).isEmpty());
        assertTrue(dataMapper.toEntityList(null).isEmpty());
        assertTrue(dataMapper.toEntityList(List.of()).isEmpty());
    }

    @Test
    void publicPersistenceAbstractionsShouldUseJavaStyleTypeParameterNames() {
        assertThat(typeParameterNames(DataMapper.class)).containsExactly("T", "ID", "D", "K");
        assertThat(typeParameterNames(AbstractAggregateRepository.class)).containsExactly("T", "ID");
    }

    @Test
    void persistenceRepositoryConstructorShouldNotExposeAggregatePersistenceObserver() {
        assertThat(constructorParameterTypes(AbstractAggregateRepository.class))
                .noneMatch(parameterTypes -> parameterTypes.contains(AggregatePersistenceObserver.class)
                        || parameterTypes.contains(AggregatePersistenceObserverAware.class));
    }

    private static List<String> typeParameterNames(Class<?> type) {
        return List.of(type.getTypeParameters()).stream().map(TypeVariable::getName).toList();
    }

    private static List<List<Class<?>>> constructorParameterTypes(Class<?> type) {
        return List.of(type.getDeclaredConstructors()).stream()
                .map(Constructor::getParameterTypes)
                .map(List::of)
                .toList();
    }

    record TestId(String value) implements Identifier, Serializable {
    }

    private static final class TestData extends AggregateData<String> {
        private String name;
    }

    private static final class TestEntity extends BaseAggregateRoot<TestEntity, TestId>
            implements AggregateRoot<TestEntity, TestId> {
        private final String name;

        private TestEntity(TestId id, String name) {
            super(id);
            this.name = name;
        }
    }
}
