package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import org.jfoundry.application.lock.DistributedLock;

/// Binds the JFoundry distributed-lock interceptor to annotated CDI methods.
@Vetoed
public class HelidonDistributedLockExtension implements Extension {

    <T> void bindDistributedLock(@Observes ProcessAnnotatedType<T> event) {
        boolean matched = event.getAnnotatedType().getMethods().stream()
                .anyMatch(method -> method.isAnnotationPresent(DistributedLock.class));
        if (!matched) {
            return;
        }
        event.configureAnnotatedType().methods().forEach(method -> {
            if (method.getAnnotated().isAnnotationPresent(DistributedLock.class)) {
                method.add(HelidonDistributedLock.Literal.INSTANCE);
            }
        });
    }
}
