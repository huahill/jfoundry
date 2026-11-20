package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/// Architecture rules for persistence adapters.
/// <p>
/// Enforces the spec Section 6.3 constraint that transaction boundaries belong to the application
/// layer and persistence adapters must not use {@code @Transactional}.
public final class PersistenceRules {

    private static final String COMPONENT_ANNOTATION = "org.springframework.stereotype.Component";
    private static final String TRANSACTIONAL_ANNOTATION = "org.springframework.transaction.annotation.Transactional";

    private PersistenceRules() {
    }

    /// Persistence implementation packages must not use {@code @Transactional} at class or method level.
    /// <p>
    /// Prevents future accidental {@code @Transactional} annotations on repository implementations
    /// after the transaction-boundary contract was clarified.
    /// <p>
    /// ArchUnit 1.4.2 has no {@code haveMethodsAnnotatedWith} API, so this rule uses a custom
    /// {@link ArchCondition} to check both class-level and method-level {@code @Transactional}
    /// annotations, including meta-annotations.
    /// <p>
    /// {@code allowEmptyShould(true)} is required because this rule is distributed by the framework
    /// and applications may reference it before adding persistence implementations or
    /// auto-configuration modules. Empty matches should pass vacuously for reusable library rules,
    /// matching {@link ValueObjectRules}.
    @ArchTest
    public static final ArchRule persistence_repository_must_not_use_transactional =
            noClasses()
                    .that().resideInAPackage("..infrastructure.persistence..")
                    .should(haveTransactionalAtClassOrMethodLevel())
                    .allowEmptyShould(true)
                    .because("Transaction boundaries belong to the application layer; "
                            + "persistence-layer @Transactional usage indicates transaction-boundary contract drift");

    /// Auto-configuration modules must not use {@code @Component}.
    /// <p>
    /// Auto-configuration classes should use {@code @AutoConfiguration} and {@code @Bean}; they
    /// should not rely on component scanning.
    /// <p>
    /// {@code allowEmptyShould(true)} is required because this rule is distributed by the framework
    /// and applications may reference it before adding auto-configuration modules. Empty matches
    /// should pass vacuously, matching {@link ValueObjectRules}.
    @ArchTest
    public static final ArchRule autoconfig_must_not_use_component =
            noClasses()
                    .that().resideInAPackage("..autoconfigure..")
                    .should(beAnnotatedWithOrMetaAnnotatedWith(COMPONENT_ANNOTATION, "Component"))
                    .allowEmptyShould(true)
                    .because("Auto-configuration modules should use @AutoConfiguration and @Bean; "
                            + "@Component/@ComponentScan usage is forbidden");

    private static ArchCondition<JavaClass> haveTransactionalAtClassOrMethodLevel() {
        return new ArchCondition<JavaClass>("be annotated with @Transactional at class or method level") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.isAnnotatedWith(TRANSACTIONAL_ANNOTATION) || item.isMetaAnnotatedWith(TRANSACTIONAL_ANNOTATION)) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getSimpleName() + " is annotated with @Transactional at class level"));
                    return;
                }
                for (JavaMethod method : item.getMethods()) {
                    if (method.isAnnotatedWith(TRANSACTIONAL_ANNOTATION) || method.isMetaAnnotatedWith(TRANSACTIONAL_ANNOTATION)) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getSimpleName() + "#" + method.getName()
                                        + " is annotated with @Transactional at method level"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beAnnotatedWithOrMetaAnnotatedWith(String annotationTypeName,
                                                                              String simpleName) {
        String desc = "be annotated with @" + simpleName + " (directly or meta)";
        return new ArchCondition<JavaClass>(desc) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.isAnnotatedWith(annotationTypeName) || item.isMetaAnnotatedWith(annotationTypeName)) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getSimpleName() + " is annotated with @" + simpleName));
                }
            }
        };
    }
}
