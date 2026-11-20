package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CqrsRulesTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter();

    @Test
    void exposesCqrsRules() {
        assertThat(JFoundryRules.cqrs().getDefinitionLocation()).isEqualTo(CqrsRules.class);
    }

    @Test
    void commandTypesMustNotDependOnInfrastructurePackages() {
        assertThatThrownBy(() -> CqrsRules.commands_must_not_depend_on_infrastructure
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.command")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.commands_must_not_depend_on_infrastructure
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.command"));
    }

    @Test
    void commandTypesMustResideInPrimaryOrApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.commands_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.commands_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.command"));
    }

    @Test
    void queryModelsMustNotResideInDomainPackages() {
        assertThatThrownBy(() -> CqrsRules.query_models_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.domain")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.query_models_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.query"));
    }

    @Test
    void queryModelsMustResideInPrimaryOrApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.query_models_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.query_models_must_reside_in_primary_or_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.query"));
    }

    @Test
    void commandHandlersMustResideInApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.command_handlers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.adapter")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.command_handlers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.application"));
    }

    @Test
    void commandDispatchersMustResideInApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.command_dispatchers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.adapter")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.command_dispatchers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.application"));
    }

    @Test
    void composedCommandHandlerAnnotationsMustResideInApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.command_handlers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.composed")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.command_handlers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.application.composed"));
    }

    @Test
    void composedCommandDispatcherAnnotationsMustResideInApplicationPackages() {
        assertThatThrownBy(() -> CqrsRules.command_dispatchers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.composed")))
                .isInstanceOf(AssertionError.class);

        CqrsRules.command_dispatchers_must_reside_in_application_packages
                .check(IMPORTER.importPackages("io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.application.composed"));
    }
}
