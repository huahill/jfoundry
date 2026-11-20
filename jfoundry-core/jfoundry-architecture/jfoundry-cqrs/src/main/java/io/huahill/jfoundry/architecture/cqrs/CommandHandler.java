package io.huahill.jfoundry.architecture.cqrs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a CQRS command handler.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR})
@Documented
@org.jmolecules.architecture.cqrs.CommandHandler
public @interface CommandHandler {

    String namespace() default "";

    String name() default "";
}
