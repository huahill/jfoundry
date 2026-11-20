package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jmolecules.architecture.cqrs.Command;
import org.jmolecules.architecture.cqrs.CommandDispatcher;
import org.jmolecules.architecture.cqrs.CommandHandler;
import org.jmolecules.architecture.cqrs.QueryModel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/// CQRS architecture rules.
/// <p>
/// These rules are intentionally small and optional. They guard the common CQRS direction:
/// command objects stay technology-neutral, command/query stereotypes describe primary
/// or application entry semantics, and command handling/dispatching behavior belongs to
/// the application layer.
public final class CqrsRules {

    private static final String[] APPLICATION_PACKAGES = {
            "..app..",
            "..application..",
            "..usecase..",
            "..usecases.."
    };

    private static final String[] PRIMARY_OR_APPLICATION_CQRS_PACKAGES = {
            "..app..",
            "..application..",
            "..usecase..",
            "..usecases..",
            "..port.in..",
            "..ports.in..",
            "..port.inbound..",
            "..ports.inbound..",
            "..command..",
            "..commands..",
            "..query..",
            "..queries.."
    };

    private static final String[] CQRS_FORBIDDEN_PACKAGES = {
            "..adapter..",
            "..adapters..",
            "..controller..",
            "..controllers..",
            "..domain..",
            "..infrastructure..",
            "..infra..",
            "..mybatis..",
            "..mapper..",
            "..data..",
            "..persistence..",
            "..port.out..",
            "..ports.out..",
            "..port.outbound..",
            "..ports.outbound.."
    };

    private CqrsRules() {
    }

    /// Command types should be transport- and persistence-neutral.
    @ArchTest
    public static final ArchRule commands_must_not_depend_on_infrastructure =
            noClasses()
                    .that().areAnnotatedWith(Command.class)
                    .or().areMetaAnnotatedWith(Command.class)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..adapter..",
                            "..adapters..",
                            "..infrastructure..",
                            "..infra..",
                            "..persistence.."
                    )
                    .allowEmptyShould(true)
                    .because("commands describe write intent and should not depend on adapters or infrastructure");

    /// Command types describe primary/application entry intent.
    @ArchTest
    public static final ArchRule commands_must_reside_in_primary_or_application_packages =
            classes()
                    .that().areAnnotatedWith(Command.class)
                    .or().areMetaAnnotatedWith(Command.class)
                    .should(resideInPrimaryOrApplicationCqrsPackage())
                    .allowEmptyShould(true)
                    .because("commands describe use-case intent and should stay in primary or application entry packages");

    /// Query models describe primary/application query outputs.
    @ArchTest
    public static final ArchRule query_models_must_reside_in_primary_or_application_packages =
            classes()
                    .that().areAnnotatedWith(QueryModel.class)
                    .or().areMetaAnnotatedWith(QueryModel.class)
                    .should(resideInPrimaryOrApplicationCqrsPackage())
                    .allowEmptyShould(true)
                    .because("query models describe query use-case outputs and should stay in primary or application entry packages");

    /// Command handlers orchestrate write use cases from the application layer.
    @ArchTest
    public static final ArchRule command_handlers_must_reside_in_application_packages =
            members()
                    .that().areAnnotatedWith(CommandHandler.class)
                    .or().areMetaAnnotatedWith(CommandHandler.class)
                    .should().beDeclaredInClassesThat().resideInAnyPackage(APPLICATION_PACKAGES)
                    .allowEmptyShould(true)
                    .because("command handlers, including composed handler annotations, orchestrate application write use cases");

    /// Command dispatchers route commands from the application layer.
    @ArchTest
    public static final ArchRule command_dispatchers_must_reside_in_application_packages =
            members()
                    .that().areAnnotatedWith(CommandDispatcher.class)
                    .or().areMetaAnnotatedWith(CommandDispatcher.class)
                    .should().beDeclaredInClassesThat().resideInAnyPackage(APPLICATION_PACKAGES)
                    .allowEmptyShould(true)
                    .because("command dispatching, including composed dispatcher annotations, is application-layer orchestration");

    private static ArchCondition<JavaClass> resideInPrimaryOrApplicationCqrsPackage() {
        return new ArchCondition<>("reside in primary or application CQRS packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String packageName = item.getPackageName();
                boolean inAllowedPackage = matchesAnyPackagePattern(packageName, PRIMARY_OR_APPLICATION_CQRS_PACKAGES);
                boolean inForbiddenPackage = matchesAnyPackagePattern(packageName, CQRS_FORBIDDEN_PACKAGES);
                if (inAllowedPackage && !inForbiddenPackage) {
                    events.add(SimpleConditionEvent.satisfied(item,
                            item.getName() + " resides in " + packageName));
                    return;
                }
                events.add(SimpleConditionEvent.violated(item,
                        item.getName() + " resides in " + packageName
                                + ", but CQRS command/query stereotypes are reserved for primary or application entry semantics"));
            }
        };
    }

    private static boolean matchesAnyPackagePattern(String packageName, String[] patterns) {
        for (String pattern : patterns) {
            if (matchesPackagePattern(packageName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPackagePattern(String packageName, String pattern) {
        String segmentPath = pattern.replace("..", "");
        return packageName.equals(segmentPath)
                || packageName.startsWith(segmentPath + ".")
                || packageName.endsWith("." + segmentPath)
                || packageName.contains("." + segmentPath + ".");
    }
}
