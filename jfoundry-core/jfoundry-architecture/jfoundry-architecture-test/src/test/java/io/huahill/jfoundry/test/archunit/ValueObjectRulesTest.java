package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// ValueObjectRules must be declared and must pass against jfoundry's own source
/// (no non-final ValueObject, no mutable ValueObject fields, all have equals/hashCode via records).
class ValueObjectRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("io.huahill.jfoundry");

    @Test
    void valueObjectRulesAreDeclared() {
        assertThat(ValueObjectRules.value_objects_must_be_final).isNotNull();
        assertThat(ValueObjectRules.value_object_fields_must_be_final).isNotNull();
        assertThat(ValueObjectRules.value_objects_must_implement_equals_and_hashCode).isNotNull();
    }

    @Test
    void jfoundryOwnValueObjectsPassRules() {
        ValueObjectRules.value_objects_must_be_final.check(classes);
        ValueObjectRules.value_object_fields_must_be_final.check(classes);
        ValueObjectRules.value_objects_must_implement_equals_and_hashCode.check(classes);
    }
}
