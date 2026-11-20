package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Sanity test: PersistenceRules constants must be non-null ArchRule instances,
/// and the rule set must pass against jfoundry's own source.
class PersistenceRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("io.huahill.jfoundry");

    @Test
    void persistenceRulesAreDeclared() {
        assertThat(PersistenceRules.persistence_repository_must_not_use_transactional)
                .as("PersistenceRules.persistence_repository_must_not_use_transactional must be non-null")
                .isNotNull();
        assertThat(PersistenceRules.autoconfig_must_not_use_component)
                .as("PersistenceRules.autoconfig_must_not_use_component must be non-null")
                .isNotNull();
    }

    @Test
    void jfoundryOwnSourceHasNoTransactionalInPersistence() {
        PersistenceRules.persistence_repository_must_not_use_transactional.check(classes);
    }

    @Test
    void jfoundryOwnSourceHasNoComponentInAutoconfigure() {
        PersistenceRules.autoconfig_must_not_use_component.check(classes);
    }
}
