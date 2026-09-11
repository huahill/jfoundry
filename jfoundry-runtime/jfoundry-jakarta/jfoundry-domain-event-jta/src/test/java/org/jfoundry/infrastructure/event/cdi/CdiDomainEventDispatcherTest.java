package org.jfoundry.infrastructure.event.cdi;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class CdiDomainEventDispatcherTest {

    @Test
    void isCdiManagedAndProxyable() {
        assertThat(Modifier.isFinal(CdiDomainEventDispatcher.class.getModifiers())).isFalse();
        assertThat(CdiDomainEventDispatcher.class.getDeclaredConstructors()).anyMatch(constructor ->
                constructor.isAnnotationPresent(Inject.class)
                        && constructor.getParameterCount() == 1);
        assertThat(CdiDomainEventDispatcher.class.getDeclaredConstructors()).anyMatch(constructor ->
                constructor.getParameterCount() == 0
                        && !Modifier.isPrivate(constructor.getModifiers()));
    }
}
