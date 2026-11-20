package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Architecture style rule: one analyzed scope should choose one primary architecture style;
/// Hexagonal and Onion are mutually exclusive.
class ArchitectureStyleRulesTest {

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS);

    @Test
    void ruleIsDeclared() {
        assertThat(ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed).isNotNull();
    }

    @Test
    void requiresHexagonalArchitectureToBeDeclared() {
        assertStyleDeclarationRequired(ArchitectureStyleRules.hexagonal_must_be_declared);
    }

    @Test
    void requiresOnionSimpleArchitectureToBeDeclared() {
        assertStyleDeclarationRequired(ArchitectureStyleRules.onion_simple_must_be_declared);
    }

    @Test
    void requiresOnionClassicalArchitectureToBeDeclared() {
        assertStyleDeclarationRequired(ArchitectureStyleRules.onion_classical_must_be_declared);
    }

    @Test
    void allowsHexagonalWithoutOnion() {
        JavaClasses classes = importer.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonal");

        ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed.check(classes);
    }

    @Test
    void allowsOnionWithoutHexagonal() {
        JavaClasses classes = importer.importPackages("io.huahill.jfoundry.test.archunit.fixture.onion");

        ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed.check(classes);
    }

    @Test
    void allowsPackageLevelHexagonalWithoutOnion() {
        JavaClasses classes = importer.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalpackage");

        ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed.check(classes);
    }

    @Test
    void rejectsMixingHexagonalAndOnionInOneAnalyzedScope() {
        JavaClasses classes = importer.importPackages("io.huahill.jfoundry.test.archunit.fixture.mixedstyles");
        ArchRule rule = ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed;

        assertThatThrownBy(() -> rule.check(classes))
                .hasMessageContaining("Hexagonal and Onion architecture styles must not be mixed");
    }

    private void assertStyleDeclarationRequired(ArchRule rule) {
        JavaClasses classes = importer.importPackages("com.example.jfoundryfixture");

        assertThatThrownBy(() -> rule.check(classes))
                .hasMessageContaining("must be declared");
    }
}
