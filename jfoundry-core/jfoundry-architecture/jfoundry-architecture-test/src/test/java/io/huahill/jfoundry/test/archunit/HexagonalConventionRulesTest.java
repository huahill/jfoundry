package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.huahill.jfoundry.test.archunit.HexagonalAdapterPackageConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HexagonalConventionRulesTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter();

    @Test
    void exposesHexagonalConventionRules() {
        assertThat(JFoundryRules.hexagonalConventions().getDefinitionLocation())
                .isEqualTo(HexagonalConventionRules.class);
    }

    @Test
    void exposesStrictHexagonalRules() {
        assertThat(JFoundryRules.hexagonalStrict().getDefinitionLocation())
                .isEqualTo(JFoundryRuleSets.HexagonalStrict.class);
    }

    @Test
    void primaryPortsMustBeInterfacesInInboundPortPackages() {
        assertThatThrownBy(() -> HexagonalConventionRules.primary_ports_must_be_interfaces
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryport")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> HexagonalConventionRules.primary_ports_must_reside_in_inbound_port_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryport")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.primary_ports_must_be_interfaces
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));
        HexagonalConventionRules.primary_ports_must_reside_in_inbound_port_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));
    }

    @Test
    void secondaryPortsMustBeInterfacesInOutboundPortPackages() {
        assertThatThrownBy(() -> HexagonalConventionRules.secondary_ports_must_be_interfaces
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.secondaryport")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> HexagonalConventionRules.secondary_ports_must_reside_in_outbound_port_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.secondaryport")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.secondary_ports_must_be_interfaces
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));
        HexagonalConventionRules.secondary_ports_must_reside_in_outbound_port_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));

        HexagonalConventionRules.secondary_ports_must_reside_in_outbound_port_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid"));
    }

    @Test
    void applicationsMustStayOutOfAdapterPackages() {
        assertThatThrownBy(() -> HexagonalConventionRules.applications_must_not_reside_in_adapter_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.application")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.applications_must_not_reside_in_adapter_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));
    }

    @Test
    void primaryAdaptersMustNotDependOnSecondarySide() {
        assertThatThrownBy(() -> HexagonalConventionRules.primary_adapters_must_not_depend_on_secondary_ports_or_adapters
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryadapter")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.primary_adapters_must_not_depend_on_secondary_ports_or_adapters
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));

        assertThatThrownBy(() -> HexagonalConventionRules.primary_adapters_must_not_depend_on_secondary_ports_or_adapters
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.invalid.primaryadapter")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void secondaryAdaptersShouldImplementSecondaryPorts() {
        assertThatThrownBy(() -> HexagonalConventionRules.secondary_adapters_should_implement_secondary_ports
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.secondaryadapter")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.secondary_adapters_should_implement_secondary_ports
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));

        HexagonalConventionRules.secondary_adapters_should_implement_secondary_ports
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid"));
    }

    @Test
    void secondarySideMustNotDependOnCqrsStereotypes() {
        assertThatThrownBy(() -> HexagonalConventionRules.secondary_side_must_not_depend_on_cqrs_stereotypes
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary",
                        "io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.query")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void primaryAndSecondaryPortsMustNotCrossDirectionalPackageBoundaries() {
        assertThatThrownBy(() -> HexagonalConventionRules.primary_ports_must_not_depend_on_outbound_port_packages
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.primary")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> HexagonalConventionRules.secondary_ports_must_not_depend_on_inbound_port_packages
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.portdirection.secondary")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.primary_ports_must_not_depend_on_outbound_port_packages
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability"));
        HexagonalConventionRules.secondary_ports_must_not_depend_on_inbound_port_packages
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.capability"));
    }

    @Test
    void applicationAndDomainMustNotDependOnPersistenceDetails() {
        assertThatThrownBy(() -> HexagonalConventionRules.application_and_domain_must_not_depend_on_persistence_details
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.persistenceleak")))
                .isInstanceOf(AssertionError.class);

        HexagonalConventionRules.application_and_domain_must_not_depend_on_persistence_details
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid"));
    }

    @Test
    void selectedAdapterPackageConventionAcceptsOnlyItsConfiguredDirectionVocabulary() {
        JFoundryRules.hexagonalAdapterPackageConvention(HexagonalAdapterPackageConvention.IN_OUT)
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.adapter"));
        JFoundryRules.hexagonalAdapterPackageConvention(HexagonalAdapterPackageConvention.PRIMARY_SECONDARY)
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonaladapterpackages.primarysecondary"));

        assertThatThrownBy(() -> JFoundryRules
                .hexagonalAdapterPackageConvention(HexagonalAdapterPackageConvention.IN_OUT)
                .check(IMPORTER.importPackages(
                        "io.huahill.jfoundry.test.archunit.fixture.hexagonaladapterpackages.primarysecondary")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> JFoundryRules
                .hexagonalAdapterPackageConvention(HexagonalAdapterPackageConvention.PRIMARY_SECONDARY)
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.adapter")))
                .isInstanceOf(AssertionError.class);
    }
}
