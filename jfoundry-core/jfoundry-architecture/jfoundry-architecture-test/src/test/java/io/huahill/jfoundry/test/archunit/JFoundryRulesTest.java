package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.junit.ArchTests;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// JFoundryRules must expose explicit primary-style entrypoints, not a catch-all all().
class JFoundryRulesTest {

    @Test
    void exposesRuleGroupsAsNativeArchTests() throws NoSuchMethodException {
        assertThat(JFoundryRules.class.getMethod("hexagonal").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("hexagonalConventions").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("hexagonalStrict").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("onionSimple").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("onionClassical").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("cqrs").getReturnType()).isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("aggregateRepositoryConventions").getReturnType())
                .isEqualTo(ArchTests.class);
        assertThat(JFoundryRules.class.getMethod("jmoleculesDdd").getReturnType()).isEqualTo(ArchTests.class);
    }

    @Test
    void doesNotExposeAllAggregator() {
        assertThatThrownBy(() -> JFoundryRules.class.getDeclaredMethod("all"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void doesNotExposeArchitectureStyleCombinations() {
        assertThatThrownBy(() -> JFoundryRules.class.getMethod("layered"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> JFoundryRules.class.getMethod("layeredHexagonal"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> JFoundryRules.class.getMethod("layeredOnionSimple"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> JFoundryRules.class.getMethod("layeredOnionClassical"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void doesNotExposeMixedJmoleculesNativeAggregator() {
        assertThatThrownBy(() -> JFoundryRules.class.getMethod("jmoleculesNative"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void jmoleculesDddReturnsDddRulesOnly() {
        assertThat(JFoundryRules.jmoleculesDdd().getDefinitionLocation())
                .isEqualTo(JFoundryRuleSets.JmoleculesDdd.class);
    }

    @Test
    void exposesArchitectureStylesExplicitly() {
        assertThat(JFoundryRules.hexagonal().getDefinitionLocation()).isEqualTo(JFoundryRuleSets.Hexagonal.class);
        assertThat(JFoundryRules.onionSimple().getDefinitionLocation()).isEqualTo(JFoundryRuleSets.OnionSimple.class);
        assertThat(JFoundryRules.onionClassical().getDefinitionLocation())
                .isEqualTo(JFoundryRuleSets.OnionClassical.class);
        assertThat(JFoundryRules.noMixedHexagonalAndOnion()).isNotNull();
    }

    @Test
    void explicitArchitectureStylesAreNonNull() {
        assertThat(JFoundryRules.hexagonal()).isNotNull();
        assertThat(JFoundryRules.onionSimple()).isNotNull();
        assertThat(JFoundryRules.onionClassical()).isNotNull();
        assertThat(JFoundryRules.jmoleculesDdd()).isNotNull();
    }
}
