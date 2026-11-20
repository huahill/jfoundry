package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;
import java.util.function.Predicate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/// Optional conventions for aggregate repository boundaries.
/// <p>
/// These rules guard against leaking generic persistence/query APIs into aggregate repositories.
/// They intentionally do not judge every business-named repository method. Command-side lookup
/// methods can still be valid when they load aggregates for immediate business behavior.
public final class AggregateRepositoryConventionRules {

    private AggregateRepositoryConventionRules() {
    }

    /// Aggregate repository interfaces must not expose generic query condition APIs such as MyBatis-Plus Wrapper
    /// or Spring Data JPA Specification types in method signatures or superinterfaces.
    @ArchTest
    public static final ArchRule aggregate_repositories_must_not_expose_query_condition_types =
            classes()
                    .that(DddRepositoryTypes.areRepositoryInterfaces())
                    .should(exposeTypesMatching(AggregateRepositoryConventionRules::isQueryConditionType,
                            "expose generic query condition types"))
                    .allowEmptyShould(true)
                    .because("aggregate repositories should expose business-named boundaries instead of generic query conditions");

    /// Aggregate repository interfaces must not expose paging APIs. Paging belongs to read/lookup ports.
    @ArchTest
    public static final ArchRule aggregate_repositories_must_not_expose_paging_types =
            classes()
                    .that(DddRepositoryTypes.areRepositoryInterfaces())
                    .should(exposeTypesMatching(AggregateRepositoryConventionRules::isPagingType,
                            "expose paging types"))
                    .allowEmptyShould(true)
                    .because("paging and listing use cases should go through read/lookup ports, not aggregate repositories");

    /// Aggregate repository interfaces must not expose persistence service or mapper APIs.
    @ArchTest
    public static final ArchRule aggregate_repositories_must_not_expose_persistence_service_types =
            classes()
                    .that(DddRepositoryTypes.areRepositoryInterfaces())
                    .should(exposeTypesMatching(AggregateRepositoryConventionRules::isPersistenceServiceType,
                            "expose persistence service or mapper types"))
                    .allowEmptyShould(true)
                    .because("aggregate repositories should not leak BaseMapper, IService, JpaRepository, or similar infrastructure APIs");

    private static ArchCondition<JavaClass> exposeTypesMatching(Predicate<JavaClass> predicate, String description) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                checkTypeSet(item, item.getAllInvolvedRawTypes(), predicate, events, "class signature");
                for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
                    checkTypeSet(item, codeUnit.getAllInvolvedRawTypes(), predicate, events, codeUnit.getDescription());
                }
                for (JavaField field : item.getFields()) {
                    checkTypeSet(item, field.getAllInvolvedRawTypes(), predicate, events, field.getDescription());
                }
            }
        };
    }

    private static void checkTypeSet(JavaClass owner, Set<JavaClass> types, Predicate<JavaClass> predicate,
                                     ConditionEvents events, String source) {
        for (JavaClass type : types) {
            if (predicate.test(type)) {
                events.add(SimpleConditionEvent.violated(owner,
                        owner.getName() + " " + source + " exposes " + type.getName()));
            }
        }
    }

    private static boolean isQueryConditionType(JavaClass javaClass) {
        String name = javaClass.getName();
        return name.startsWith("com.baomidou.mybatisplus.core.conditions.")
                || name.startsWith("org.springframework.data.jpa.domain.Specification");
    }

    private static boolean isPagingType(JavaClass javaClass) {
        String name = javaClass.getName();
        return name.equals("com.baomidou.mybatisplus.extension.plugins.pagination.Page")
                || name.equals("com.baomidou.mybatisplus.core.metadata.IPage")
                || name.startsWith("org.springframework.data.domain.Page")
                || name.startsWith("org.springframework.data.domain.Pageable");
    }

    private static boolean isPersistenceServiceType(JavaClass javaClass) {
        String name = javaClass.getName();
        return name.equals("com.baomidou.mybatisplus.core.mapper.BaseMapper")
                || name.equals("com.baomidou.mybatisplus.extension.service.IService")
                || name.startsWith("org.springframework.data.repository.")
                || name.startsWith("org.springframework.data.jpa.repository.");
    }
}
