package org.jfoundry.application.event.externalization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares aggregate routing metadata for externally dispatched domain events.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AggregateRouting {

    /// Aggregate type used for ordering diagnostics. Defaults to the event class simple name.
    String type() default "";

    /// Property path resolving to the aggregate id.
    String id();

    /// Optional property path resolving to the aggregate version.
    String version() default "";
}
