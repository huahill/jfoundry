package org.jfoundry.infrastructure.persistence.mybatis;

import org.jfoundry.infrastructure.persistence.AggregateEventRegistrar;
import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAggregateRepositoryApiTest {

    @Test
    void mybatisPlusRepositoryConstructorShouldNotExposeAggregateEventRegistrar() {
        assertThat(constructorParameterTypes(MybatisPlusAggregateRepository.class))
                .noneMatch(parameterTypes -> parameterTypes.contains(AggregateEventRegistrar.class));
        assertThat(MybatisPlusAggregateRepository.class.getSuperclass())
                .isEqualTo(AbstractAggregateRepository.class);
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
}
