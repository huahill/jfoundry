package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;

final class JFoundryRuleSets {

    private JFoundryRuleSets() {
    }

    static final class Baseline {

        @ArchTest
        static final ArchTests persistence = ArchTests.in(PersistenceRules.class);

        @ArchTest
        static final ArchTests valueObjects = ArchTests.in(ValueObjectRules.class);

        @ArchTest
        static final ArchTests frameworkModules = ArchTests.in(FrameworkModuleRules.class);
    }

    static final class Hexagonal {

        @ArchTest
        static final ArchTests baseline = ArchTests.in(Baseline.class);

        @ArchTest
        static final ArchRule jmolecules = JMoleculesArchitectureRules.ensureHexagonal();

        @ArchTest
        static final ArchRule architectureStyleDeclared =
                ArchitectureStyleRules.hexagonal_must_be_declared;

        @ArchTest
        static final ArchRule noMixedArchitectureStyles =
                ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed;
    }

    static final class HexagonalStrict {

        @ArchTest
        static final ArchTests architecture = ArchTests.in(Hexagonal.class);

        @ArchTest
        static final ArchTests conventions = ArchTests.in(HexagonalConventionRules.class);
    }

    static final class OnionSimple {

        @ArchTest
        static final ArchTests baseline = ArchTests.in(Baseline.class);

        @ArchTest
        static final ArchRule jmolecules = JMoleculesArchitectureRules.ensureOnionSimple();

        @ArchTest
        static final ArchRule architectureStyleDeclared =
                ArchitectureStyleRules.onion_simple_must_be_declared;

        @ArchTest
        static final ArchRule noMixedArchitectureStyles =
                ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed;
    }

    static final class OnionClassical {

        @ArchTest
        static final ArchTests baseline = ArchTests.in(Baseline.class);

        @ArchTest
        static final ArchRule jmolecules = JMoleculesArchitectureRules.ensureOnionClassical();

        @ArchTest
        static final ArchRule architectureStyleDeclared =
                ArchitectureStyleRules.onion_classical_must_be_declared;

        @ArchTest
        static final ArchRule noMixedArchitectureStyles =
                ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed;
    }

    static final class JmoleculesDdd {

        @ArchTest
        static final ArchRule aggregateReferences =
                JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation();

        @ArchTest
        static final ArchRule valueObjectReferences =
                JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables();
    }
}
