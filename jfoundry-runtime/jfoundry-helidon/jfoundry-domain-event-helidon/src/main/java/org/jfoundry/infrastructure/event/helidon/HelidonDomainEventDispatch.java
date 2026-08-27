package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// CDI interceptor binding added to JFoundry application services by the portable extension.
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HelidonDomainEventDispatch {

    /// Literal used by the portable CDI extension.
    final class Literal extends AnnotationLiteral<HelidonDomainEventDispatch> implements HelidonDomainEventDispatch {

        public static final Literal INSTANCE = new Literal();

        private Literal() {
        }
    }
}
