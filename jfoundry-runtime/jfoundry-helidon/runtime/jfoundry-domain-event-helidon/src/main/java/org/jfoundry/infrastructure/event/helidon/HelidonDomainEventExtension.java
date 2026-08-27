package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import org.jfoundry.application.ApplicationService;

/// Binds the JFoundry domain-event interceptor to CDI application-service beans.
@Vetoed
public class HelidonDomainEventExtension implements Extension {

    <T> void bindApplicationService(@Observes ProcessAnnotatedType<T> event) {
        if (event.getAnnotatedType().isAnnotationPresent(ApplicationService.class)) {
            event.configureAnnotatedType().add(HelidonDomainEventDispatch.Literal.INSTANCE);
        }
    }
}
