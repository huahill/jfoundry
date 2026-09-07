package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxTriggerCdiScopeTest {

    @Test
    void usesDependentScopeForNativeCdiCompatibility() {
        assertThat(HelidonOutboxTrigger.class.isAnnotationPresent(Dependent.class)).isTrue();
    }

    @Test
    void doesNotImplementOutboxDispatcher() {
        assertThat(org.jfoundry.application.outbox.OutboxDispatcher.class.isAssignableFrom(HelidonOutboxTrigger.class))
                .isFalse();
    }

    @Test
    void observesApplicationInitializationToStartScheduling() {
        assertThat(Arrays.stream(HelidonOutboxTrigger.class.getDeclaredMethods())
                .anyMatch(HelidonOutboxTriggerCdiScopeTest::isApplicationInitializationObserver))
                .isTrue();
    }

    private static boolean isApplicationInitializationObserver(Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        return method.getParameterCount() == 1
                && Arrays.stream(parameterAnnotations[0]).anyMatch(annotation ->
                annotation.annotationType().equals(Observes.class))
                && Arrays.stream(parameterAnnotations[0]).anyMatch(annotation ->
                annotation.annotationType().equals(Initialized.class)
                        && ((Initialized) annotation).value().equals(ApplicationScoped.class));
    }
}
