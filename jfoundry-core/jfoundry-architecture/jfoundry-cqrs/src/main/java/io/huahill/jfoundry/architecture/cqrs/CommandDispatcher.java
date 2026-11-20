package io.huahill.jfoundry.architecture.cqrs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a CQRS command dispatcher.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Documented
@org.jmolecules.architecture.cqrs.CommandDispatcher
public @interface CommandDispatcher {

    String dispatches() default "";
}
