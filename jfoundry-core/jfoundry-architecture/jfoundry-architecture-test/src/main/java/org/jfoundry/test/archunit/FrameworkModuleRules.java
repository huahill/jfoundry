package org.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/// JFoundry framework module boundary rules.
public final class FrameworkModuleRules {

    private FrameworkModuleRules() {
    }

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAPackage("org.jfoundry.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.jfoundry.application..",
                            "org.jfoundry.infrastructure..",
                            "org.jfoundry.autoconfigure..")
                    .allowEmptyShould(true)
                    .because("domain code must not depend on application, infrastructure, or autoconfigure modules");

    @ArchTest
    public static final ArchRule application_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAPackage("org.jfoundry.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.jfoundry.infrastructure..",
                            "org.jfoundry.autoconfigure..")
                    .allowEmptyShould(true)
                    .because("application code must not depend on infrastructure adapters or autoconfiguration");

    @ArchTest
    public static final ArchRule framework_should_use_jmolecules_architecture_annotations_internally =
            noClasses()
                    .that().resideInAPackage("org.jfoundry..")
                    .and().resideOutsideOfPackage("org.jfoundry.architecture..")
                    .and().doNotHaveFullyQualifiedName("org.jfoundry.test.archunit.ArchitectureStyleRules")
                    .should().dependOnClassesThat().resideInAnyPackage("org.jfoundry.architecture..")
                    .allowEmptyShould(true)
                    .because("JFoundry architecture annotations are public API wrappers; framework internals use jMolecules directly");

    @ArchTest
    public static final ArchRule domain_packages_should_be_onion_domain_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.domain..")
                    .should(resideInPackageAnnotatedWith(DomainRing.class))
                    .allowEmptyShould(true)
                    .because("domain packages are part of the Onion domain ring");

    @ArchTest
    public static final ArchRule application_packages_should_be_onion_application_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.application..")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("application packages are part of the Onion application ring");

    @ArchTest
    public static final ArchRule infrastructure_packages_should_be_onion_infrastructure_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.infrastructure..")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("infrastructure packages are part of the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule spring_autoconfigure_packages_should_not_be_onion_rings =
            classes()
                    .that().resideInAPackage("org.jfoundry.autoconfigure..")
                    .should(notResideInPackageAnnotatedWithAny(
                            DomainRing.class,
                            ApplicationRing.class,
                            InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("Spring Boot autoconfiguration assembles framework modules but is not itself an Onion ring");

    @ArchTest
    public static final ArchRule infrastructure_must_not_depend_on_spring_autoconfigure =
            noClasses()
                    .that().resideInAPackage("org.jfoundry.infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.jfoundry.autoconfigure..")
                    .allowEmptyShould(true)
                    .because("infrastructure adapters must stay independent from Spring Boot autoconfiguration");

    @ArchTest
    public static final ArchRule application_store_ports_should_be_in_application_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.application..")
                    .and().haveSimpleNameEndingWith("Store")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("application store abstractions belong to the Onion application ring");

    @ArchTest
    public static final ArchRule domain_event_dispatcher_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.event.DomainEventDispatcher")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("DomainEventDispatcher belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule domain_event_context_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.event.DomainEventContext")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("DomainEventContext belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule domain_event_outbox_recorder_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.event.outbox.DomainEventOutboxRecorder")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("DomainEventOutboxRecorder belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule message_sender_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.messaging.MessageSender")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("MessageSender belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule payload_serializer_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.messaging.PayloadSerializer")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("PayloadSerializer belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule externalization_rules_should_not_be_in_messaging_package =
            noClasses()
                    .that().resideInAPackage("org.jfoundry.application.event.externalization..")
                    .should().resideInAPackage("org.jfoundry.application.messaging..")
                    .allowEmptyShould(true)
                    .because("domain event externalization metadata is not the messaging transport SPI");

    @ArchTest
    public static final ArchRule event_externalization_rules_should_be_in_application_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.application.event.externalization..")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("domain event externalization rules belong to the Onion application ring");

    @ArchTest
    public static final ArchRule outbox_dispatcher_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.application.outbox.OutboxDispatcher")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("OutboxDispatcher belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule infrastructure_message_stores_should_be_in_infrastructure_ring =
            classes()
                    .that().resideInAPackage("org.jfoundry.infrastructure..mybatis..")
                    .and().haveSimpleNameEndingWith("MessageStore")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("MyBatis message stores belong to the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule spring_application_event_dispatcher_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName(
                            "org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("SpringApplicationEventDispatcher is a Spring adapter for domain event dispatch");

    @ArchTest
    public static final ArchRule spring_event_dispatcher_should_not_be_in_messaging_package =
            noClasses()
                    .that().haveSimpleName("SpringApplicationEventDispatcher")
                    .should().resideInAPackage("org.jfoundry.infrastructure.messaging..")
                    .allowEmptyShould(true)
                    .because("Spring ApplicationEvent publishing is a domain event adapter, not a messaging transport adapter");

    @ArchTest
    public static final ArchRule default_domain_event_outbox_recorder_should_be_in_application_ring =
            classes()
                    .that().haveFullyQualifiedName(
                            "org.jfoundry.application.event.outbox.DefaultDomainEventOutboxRecorder")
                    .should(resideInPackageAnnotatedWith(ApplicationRing.class))
                    .allowEmptyShould(true)
                    .because("DefaultDomainEventOutboxRecorder belongs to the Onion application ring");

    @ArchTest
    public static final ArchRule outbox_domain_event_dispatcher_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName(
                            "org.jfoundry.infrastructure.outbox.spring.externalization.OutboxDomainEventDispatcher")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("OutboxDomainEventDispatcher belongs to the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule kafka_message_sender_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.infrastructure.messaging.spring.sender.SpringKafkaMessageSender")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("SpringKafkaMessageSender belongs to the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule jackson_payload_serializer_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName("org.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("JacksonPayloadSerializer belongs to the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule scheduled_outbox_trigger_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName(
                            "org.jfoundry.infrastructure.outbox.spring.dispatcher.ScheduledOutboxTrigger")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("ScheduledOutboxTrigger belongs to the Onion infrastructure ring");

    @ArchTest
    public static final ArchRule jobrunr_outbox_trigger_should_be_in_infrastructure_ring =
            classes()
                    .that().haveFullyQualifiedName(
                            "org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger")
                    .should(resideInPackageAnnotatedWith(InfrastructureRing.class))
                    .allowEmptyShould(true)
                    .because("JobRunrOutboxTrigger belongs to the Onion infrastructure ring");

    private static ArchCondition<JavaClass> resideInPackageAnnotatedWith(
            Class<? extends java.lang.annotation.Annotation> annotationType) {
        return new ArchCondition<JavaClass>("reside in package annotated with @"
                + annotationType.getSimpleName()) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isPackageAnnotatedWith(item, annotationType)) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " resides in package " + item.getPackageName()
                                    + " without @" + annotationType.getSimpleName()));
                }
            }
        };
    }

    @SafeVarargs
    private static ArchCondition<JavaClass> notResideInPackageAnnotatedWithAny(
            Class<? extends java.lang.annotation.Annotation>... annotationTypes) {
        return new ArchCondition<JavaClass>("not reside in package annotated with one of the given roles") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Class<? extends java.lang.annotation.Annotation> annotationType : annotationTypes) {
                    if (isPackageAnnotatedWith(item, annotationType)) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " resides in package " + item.getPackageName()
                                        + " with forbidden @" + annotationType.getSimpleName()));
                        return;
                    }
                }
            }
        };
    }

    private static boolean isPackageAnnotatedWith(
            JavaClass javaClass,
            Class<? extends java.lang.annotation.Annotation> annotationType) {
        JavaPackage current = javaClass.getPackage();
        while (current != null) {
            if (current.isAnnotatedWith(annotationType.getName())) {
                return true;
            }
            current = current.getParent().orElse(null);
        }
        return false;
    }
}
