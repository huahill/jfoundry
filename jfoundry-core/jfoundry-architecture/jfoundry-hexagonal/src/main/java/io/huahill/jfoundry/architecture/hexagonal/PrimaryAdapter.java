package io.huahill.jfoundry.architecture.hexagonal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks an adapter that drives the application through a primary port.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Documented
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
public @interface PrimaryAdapter {
}
