package io.huahill.jfoundry.architecture.cqrs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a CQRS query model.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@org.jmolecules.architecture.cqrs.QueryModel
public @interface QueryModel {
}
