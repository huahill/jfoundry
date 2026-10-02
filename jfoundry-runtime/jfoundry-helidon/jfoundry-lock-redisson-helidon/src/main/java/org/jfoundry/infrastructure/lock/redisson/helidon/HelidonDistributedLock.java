package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// CDI interceptor binding added to {@code @DistributedLock} methods by the portable extension.
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HelidonDistributedLock {

    /// Literal used by the portable CDI extension.
    final class Literal extends AnnotationLiteral<HelidonDistributedLock> implements HelidonDistributedLock {

        public static final Literal INSTANCE = new Literal();

        private Literal() {
        }
    }
}
