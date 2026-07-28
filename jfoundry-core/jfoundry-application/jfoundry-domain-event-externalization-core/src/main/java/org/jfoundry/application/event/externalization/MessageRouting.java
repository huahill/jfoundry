package org.jfoundry.application.event.externalization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Message routing metadata. This has higher priority than
/// {@link org.jmolecules.event.annotation.Externalized#value()}.
/// <p>
/// This annotation only declares routing metadata such as topic and key.
/// Whether the event is externalized is still controlled by {@code @Externalized}.
/// If an event class has {@code @MessageRouting} without {@code @Externalized},
/// the resolver logs a warning but does not fail.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageRouting {

    /// Externalization target topic.
    String topic();

    /// Routing-key property expression. The root object is the event instance.
    /// An empty string means there is no routing key.
    String key() default "";
}
