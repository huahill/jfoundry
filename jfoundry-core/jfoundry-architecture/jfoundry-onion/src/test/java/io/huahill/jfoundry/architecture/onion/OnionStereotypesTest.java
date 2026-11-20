package io.huahill.jfoundry.architecture.onion;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

class OnionStereotypesTest {

    @Test
    void simplifiedStereotypesAreMetaAnnotated() {
        assertThat(io.huahill.jfoundry.architecture.onion.simplified.DomainRing.class.getAnnotation(
                org.jmolecules.architecture.onion.simplified.DomainRing.class)).isNotNull();
        assertThat(io.huahill.jfoundry.architecture.onion.simplified.ApplicationRing.class.getAnnotation(
                org.jmolecules.architecture.onion.simplified.ApplicationRing.class)).isNotNull();
        assertThat(io.huahill.jfoundry.architecture.onion.simplified.InfrastructureRing.class.getAnnotation(
                org.jmolecules.architecture.onion.simplified.InfrastructureRing.class)).isNotNull();
    }

    @Test
    void classicalStereotypesAreMetaAnnotated() {
        assertThat(io.huahill.jfoundry.architecture.onion.classical.DomainModelRing.class.getAnnotation(
                org.jmolecules.architecture.onion.classical.DomainModelRing.class)).isNotNull();
        assertThat(io.huahill.jfoundry.architecture.onion.classical.DomainServiceRing.class.getAnnotation(
                org.jmolecules.architecture.onion.classical.DomainServiceRing.class)).isNotNull();
        assertThat(io.huahill.jfoundry.architecture.onion.classical.ApplicationServiceRing.class.getAnnotation(
                org.jmolecules.architecture.onion.classical.ApplicationServiceRing.class)).isNotNull();
        assertThat(io.huahill.jfoundry.architecture.onion.classical.InfrastructureRing.class.getAnnotation(
                org.jmolecules.architecture.onion.classical.InfrastructureRing.class)).isNotNull();
    }

    @Test
    void allStereotypesTargetPackageAndType() {
        for (Class<? extends Annotation> stereotype : new Class[]{
                io.huahill.jfoundry.architecture.onion.simplified.DomainRing.class,
                io.huahill.jfoundry.architecture.onion.simplified.ApplicationRing.class,
                io.huahill.jfoundry.architecture.onion.simplified.InfrastructureRing.class,
                io.huahill.jfoundry.architecture.onion.classical.DomainModelRing.class,
                io.huahill.jfoundry.architecture.onion.classical.DomainServiceRing.class,
                io.huahill.jfoundry.architecture.onion.classical.ApplicationServiceRing.class,
                io.huahill.jfoundry.architecture.onion.classical.InfrastructureRing.class
        }) {
            java.lang.annotation.Target target = stereotype.getAnnotation(java.lang.annotation.Target.class);
            assertThat(target).as(stereotype.getSimpleName() + " must have @Target").isNotNull();
            assertThat(java.util.Arrays.asList(target.value()))
                    .as(stereotype.getSimpleName() + " must target PACKAGE and TYPE")
                    .contains(java.lang.annotation.ElementType.PACKAGE, java.lang.annotation.ElementType.TYPE);
        }
    }
}
