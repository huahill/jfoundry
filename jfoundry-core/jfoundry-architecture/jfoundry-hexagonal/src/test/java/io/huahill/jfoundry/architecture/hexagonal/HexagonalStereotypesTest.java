package io.huahill.jfoundry.architecture.hexagonal;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HexagonalStereotypesTest {

    @Test
    void allStereotypesAreMetaAnnotated() {
        assertThat(Application.class.getAnnotation(org.jmolecules.architecture.hexagonal.Application.class))
                .as("@Application must be meta-annotated with jMolecules @Application")
                .isNotNull();
        assertThat(PrimaryAdapter.class.getAnnotation(org.jmolecules.architecture.hexagonal.PrimaryAdapter.class))
                .as("@PrimaryAdapter must be meta-annotated with jMolecules @PrimaryAdapter")
                .isNotNull();
        assertThat(PrimaryPort.class.getAnnotation(org.jmolecules.architecture.hexagonal.PrimaryPort.class))
                .as("@PrimaryPort must be meta-annotated with jMolecules @PrimaryPort")
                .isNotNull();
        assertThat(SecondaryAdapter.class.getAnnotation(org.jmolecules.architecture.hexagonal.SecondaryAdapter.class))
                .as("@SecondaryAdapter must be meta-annotated with jMolecules @SecondaryAdapter")
                .isNotNull();
        assertThat(SecondaryPort.class.getAnnotation(org.jmolecules.architecture.hexagonal.SecondaryPort.class))
                .as("@SecondaryPort must be meta-annotated with jMolecules @SecondaryPort")
                .isNotNull();
    }

    @Test
    void allStereotypesTargetPackageAndType() {
        for (Class<? extends Annotation> stereotype : new Class[]{
                Application.class, PrimaryAdapter.class, PrimaryPort.class, SecondaryAdapter.class, SecondaryPort.class
        }) {
            java.lang.annotation.Target target = stereotype.getAnnotation(java.lang.annotation.Target.class);
            assertThat(target).as(stereotype.getSimpleName() + " must have @Target").isNotNull();
            assertThat(java.util.Arrays.asList(target.value()))
                    .as(stereotype.getSimpleName() + " must target PACKAGE and TYPE")
                    .contains(java.lang.annotation.ElementType.PACKAGE, java.lang.annotation.ElementType.TYPE);
        }
    }

    @Test
    void jfoundryStereotypesDoNotRedeclareJmoleculesAttributes() {
        for (Class<? extends Annotation> stereotype : new Class[]{
                PrimaryAdapter.class, PrimaryPort.class, SecondaryAdapter.class, SecondaryPort.class
        }) {
            assertThat(Stream.of(stereotype.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                    .as(stereotype.getSimpleName() + " must not redeclare jMolecules name/description attributes")
                    .doesNotContain("name", "description");
        }
    }

    @Test
    void genericPortAndAdapterWrappersAreNotPartOfJfoundryApi() {
        assertThat(load("io.huahill.jfoundry.architecture.hexagonal.Port")).isNull();
        assertThat(load("io.huahill.jfoundry.architecture.hexagonal.Adapter")).isNull();
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
