package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/// Architecture style selection rules.
/// <p>
/// Layered, Hexagonal, and Onion architectures all express dependency direction and boundaries.
/// Application code should choose one primary style and avoid using overlapping vocabularies for
/// the same roles within one analyzed scope.
public final class ArchitectureStyleRules {

    private ArchitectureStyleRules() {
    }

    /// Hexagonal and Onion style annotations should not appear in the same ArchUnit analysis scope.
    @ArchTest
    public static final ArchRule hexagonal_and_onion_must_not_be_mixed =
            classes()
                    .should(new HexagonalAndOnionMustNotBeMixedCondition())
                    .allowEmptyShould(true)
                    .because("Hexagonal and Onion are alternative primary architecture styles; "
                            + "choose one architecture vocabulary per analyzed scope");

    private static final class HexagonalAndOnionMustNotBeMixedCondition extends ArchCondition<JavaClass> {

        private boolean hasHexagonal;

        private boolean hasOnion;

        private String hexagonalExample;

        private String onionExample;

        HexagonalAndOnionMustNotBeMixedCondition() {
            super("not mix Hexagonal and Onion architecture styles");
        }

        @Override
        public void init(Collection<JavaClass> allClasses) {
            hasHexagonal = false;
            hasOnion = false;
            hexagonalExample = null;
            onionExample = null;
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (hasArchitectureStyleAnnotation(item, HEXAGONAL_ANNOTATIONS)) {
                hasHexagonal = true;
                if (hexagonalExample == null) {
                    hexagonalExample = item.getName();
                }
            }
            if (hasArchitectureStyleAnnotation(item, ONION_ANNOTATIONS)) {
                hasOnion = true;
                if (onionExample == null) {
                    onionExample = item.getName();
                }
            }
        }

        @Override
        public void finish(ConditionEvents events) {
            if (hasHexagonal && hasOnion) {
                String message = "Hexagonal and Onion architecture styles must not be mixed in one analyzed scope"
                        + " (hexagonal example: " + hexagonalExample
                        + ", onion example: " + onionExample + ")";
                events.add(SimpleConditionEvent.violated(this, message));
            }
        }
    }

    private static final class ArchitectureStyleMustBeDeclaredCondition extends ArchCondition<JavaClass> {

        private final String architectureStyle;

        private final Class<? extends Annotation>[] annotations;

        private boolean declared;

        ArchitectureStyleMustBeDeclaredCondition(
                String architectureStyle,
                Class<? extends Annotation>[] annotations) {
            super("declare the " + architectureStyle + " architecture style");
            this.architectureStyle = architectureStyle;
            this.annotations = annotations;
        }

        @Override
        public void init(Collection<JavaClass> allClasses) {
            declared = false;
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (hasArchitectureStyleAnnotation(item, annotations)) {
                declared = true;
            }
        }

        @Override
        public void finish(ConditionEvents events) {
            if (!declared) {
                events.add(SimpleConditionEvent.violated(
                        this,
                        architectureStyle + " architecture must be declared with at least one matching annotation"));
            }
        }
    }

    private static boolean hasArchitectureStyleAnnotation(
            JavaClass javaClass, Class<? extends Annotation>[] annotations) {
        if (isAnnotatedWithAny(javaClass, annotations)) {
            return true;
        }
        JavaPackage pkg = javaClass.getPackage();
        while (pkg != null) {
            if (isAnnotatedWithAny(pkg, annotations)) {
                return true;
            }
            pkg = pkg.getParent().orElse(null);
        }
        return false;
    }

    private static boolean isAnnotatedWithAny(JavaClass javaClass, Class<? extends Annotation>[] annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (javaClass.isAnnotatedWith(annotation.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnnotatedWithAny(JavaPackage javaPackage, Class<? extends Annotation>[] annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (javaPackage.isAnnotatedWith(annotation.getName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] HEXAGONAL_ANNOTATIONS = new Class[]{
            io.huahill.jfoundry.architecture.hexagonal.Application.class,
            io.huahill.jfoundry.architecture.hexagonal.PrimaryAdapter.class,
            io.huahill.jfoundry.architecture.hexagonal.PrimaryPort.class,
            io.huahill.jfoundry.architecture.hexagonal.SecondaryAdapter.class,
            io.huahill.jfoundry.architecture.hexagonal.SecondaryPort.class,
            org.jmolecules.architecture.hexagonal.Application.class,
            org.jmolecules.architecture.hexagonal.Adapter.class,
            org.jmolecules.architecture.hexagonal.Port.class,
            org.jmolecules.architecture.hexagonal.PrimaryAdapter.class,
            org.jmolecules.architecture.hexagonal.PrimaryPort.class,
            org.jmolecules.architecture.hexagonal.SecondaryAdapter.class,
            org.jmolecules.architecture.hexagonal.SecondaryPort.class
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] ONION_ANNOTATIONS = new Class[]{
            io.huahill.jfoundry.architecture.onion.simplified.DomainRing.class,
            io.huahill.jfoundry.architecture.onion.simplified.ApplicationRing.class,
            io.huahill.jfoundry.architecture.onion.simplified.InfrastructureRing.class,
            io.huahill.jfoundry.architecture.onion.classical.DomainModelRing.class,
            io.huahill.jfoundry.architecture.onion.classical.DomainServiceRing.class,
            io.huahill.jfoundry.architecture.onion.classical.ApplicationServiceRing.class,
            io.huahill.jfoundry.architecture.onion.classical.InfrastructureRing.class,
            org.jmolecules.architecture.onion.simplified.DomainRing.class,
            org.jmolecules.architecture.onion.simplified.ApplicationRing.class,
            org.jmolecules.architecture.onion.simplified.InfrastructureRing.class,
            org.jmolecules.architecture.onion.classical.DomainModelRing.class,
            org.jmolecules.architecture.onion.classical.DomainServiceRing.class,
            org.jmolecules.architecture.onion.classical.ApplicationServiceRing.class,
            org.jmolecules.architecture.onion.classical.InfrastructureRing.class
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] ONION_SIMPLE_ANNOTATIONS = new Class[]{
            io.huahill.jfoundry.architecture.onion.simplified.DomainRing.class,
            io.huahill.jfoundry.architecture.onion.simplified.ApplicationRing.class,
            io.huahill.jfoundry.architecture.onion.simplified.InfrastructureRing.class,
            org.jmolecules.architecture.onion.simplified.DomainRing.class,
            org.jmolecules.architecture.onion.simplified.ApplicationRing.class,
            org.jmolecules.architecture.onion.simplified.InfrastructureRing.class
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] ONION_CLASSICAL_ANNOTATIONS = new Class[]{
            io.huahill.jfoundry.architecture.onion.classical.DomainModelRing.class,
            io.huahill.jfoundry.architecture.onion.classical.DomainServiceRing.class,
            io.huahill.jfoundry.architecture.onion.classical.ApplicationServiceRing.class,
            io.huahill.jfoundry.architecture.onion.classical.InfrastructureRing.class,
            org.jmolecules.architecture.onion.classical.DomainModelRing.class,
            org.jmolecules.architecture.onion.classical.DomainServiceRing.class,
            org.jmolecules.architecture.onion.classical.ApplicationServiceRing.class,
            org.jmolecules.architecture.onion.classical.InfrastructureRing.class
    };

    /// A Hexagonal rule entrypoint should not pass when the analyzed scope declares no Hexagonal role.
    @ArchTest
    public static final ArchRule hexagonal_must_be_declared =
            classes()
                    .should(new ArchitectureStyleMustBeDeclaredCondition(
                            "Hexagonal", HEXAGONAL_ANNOTATIONS))
                    .allowEmptyShould(true);

    /// An Onion Simple rule entrypoint should not pass when the analyzed scope declares no simplified ring.
    @ArchTest
    public static final ArchRule onion_simple_must_be_declared =
            classes()
                    .should(new ArchitectureStyleMustBeDeclaredCondition(
                            "Onion Simple", ONION_SIMPLE_ANNOTATIONS))
                    .allowEmptyShould(true);

    /// An Onion Classical rule entrypoint should not pass when the analyzed scope declares no classical ring.
    @ArchTest
    public static final ArchRule onion_classical_must_be_declared =
            classes()
                    .should(new ArchitectureStyleMustBeDeclaredCondition(
                            "Onion Classical", ONION_CLASSICAL_ANNOTATIONS))
                    .allowEmptyShould(true);
}
