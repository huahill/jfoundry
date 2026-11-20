package io.huahill.jfoundry.domain.architecture;

import io.huahill.jfoundry.domain.repository.AggregateRepository;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.jmolecules.archunit.JMoleculesRules;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainArchitectureTest {

    private static final Pattern FORBIDDEN_REPOSITORY_METHOD = Pattern.compile(
            "^(findBy(?!Id$)|findOneBy|findFirstBy|countBy|page[A-Z].*|deleteBy|removeBy|clearBy|removeWhere).*$");

    @Test
    void domainShouldNotDependOnInfrastructureFrameworksOrBusinessModules() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.huahill.jfoundry.domain");

        ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "com.mysoft.ci..",
                        "io.huahill.jfoundry.infrastructure.."
                )
                .check(classes);
    }

    @Test
    void frameworkRepositoriesShouldOnlyExposeLifecycleMethods() {
        List<String> violations = new ArrayList<>();
        assertRepositoryMethods(AggregateRepository.class, violations);

        assertTrue(violations.isEmpty(), "Repository API violations:\n" + String.join("\n", violations));
    }

    @Test
    void jmoleculesDddRulesShouldBeEnforced() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.huahill.jfoundry");

        JMoleculesRules.aggregateReferencesShouldBeViaIdOrAssociation().check(classes);
    }

    private static void assertRepositoryMethods(Class<?> repositoryType, List<String> violations) {
        for (Method method : repositoryType.getDeclaredMethods()) {
            if (FORBIDDEN_REPOSITORY_METHOD.matcher(method.getName()).matches()) {
                violations.add(repositoryType.getName() + "#" + method.getName());
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                String parameterTypeName = parameterType.getName();
                if (parameterTypeName.contains(".selection.") || parameterTypeName.contains(".removal.")) {
                    violations.add(repositoryType.getName() + "#" + method.getName()
                            + " parameter uses " + parameterTypeName);
                }
            }
        }
    }
}
