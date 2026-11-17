package org.jfoundry.infrastructure.persistence.mybatis;

import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserver;
import org.jmolecules.ddd.types.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAggregateRepositoryApiTest {

    @Test
    void mybatisPlusRepositoryConstructorShouldNotExposeAggregatePersistenceObserver() {
        assertThat(constructorParameterTypes(MybatisPlusAggregateRepository.class))
                .noneMatch(parameterTypes -> parameterTypes.contains(AggregatePersistenceObserver.class));
        assertThat(MybatisPlusAggregateRepository.class.getSuperclass())
                .isEqualTo(AbstractAggregateRepository.class);
    }

    @Test
    void versionedRootLifecycleCannotBeReplacedByASubclass() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(MybatisPlusAggregateRepository.class
                .getDeclaredMethod("doAdd", AggregateRoot.class)
                .getModifiers())).isTrue();
        assertThat(Modifier.isFinal(MybatisPlusAggregateRepository.class
                .getDeclaredMethod("doModify", AggregateRoot.class)
                .getModifiers())).isTrue();
        assertThat(Modifier.isFinal(MybatisPlusAggregateRepository.class
                .getDeclaredMethod("doRemove", AggregateRoot.class)
                .getModifiers())).isTrue();
    }

    @Test
    void dependentWritesRemainPartOfTheAggregateWrite() throws NoSuchMethodException {
        assertOverridable("afterInsert", AggregateRoot.class, MybatisPlusAggregateData.class);
        assertOverridable("afterUpdate", AggregateRoot.class, MybatisPlusAggregateData.class);
        assertOverridable("afterRemove", AggregateRoot.class);
    }

    @Test
    void persistenceContextAwarenessShouldRemainProxyable() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(MybatisPlusAggregateRepository.class
                .getMethod("setAggregatePersistenceContext",
                        org.jfoundry.infrastructure.persistence.AggregatePersistenceContext.class)
                .getModifiers())).isFalse();
    }

    private static List<List<Class<?>>> constructorParameterTypes(Class<?> type) {
        return List.of(type.getDeclaredConstructors()).stream()
                .map(Constructor::getParameterTypes)
                .map(List::of)
                .toList();
    }

    private static void assertOverridable(String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = MybatisPlusAggregateRepository.class.getDeclaredMethod(name, parameterTypes);
        assertThat(Modifier.isProtected(method.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(method.getModifiers())).isFalse();
    }
}
