package io.huahill.jfoundry.architecture.onion.simplified;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks the domain ring in simplified Onion Architecture.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Documented
@org.jmolecules.architecture.onion.simplified.DomainRing
public @interface DomainRing {
}
