package org.jfoundry.integration.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jfoundry.test.archunit.FrameworkModuleRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkModuleArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("org.jfoundry");

    @Test
    void importsRepresentativeClassesFromCoreInfrastructureAndSpringModules() {
        assertThat(classes).anyMatch(javaClass ->
                javaClass.getName().equals("org.jfoundry.domain.entity.BaseEntity"));
        assertThat(classes).anyMatch(javaClass ->
                javaClass.getName().equals("org.jfoundry.application.outbox.OutboxMessageStore"));
        assertThat(classes).anyMatch(javaClass ->
                javaClass.getName().equals("org.jfoundry.infrastructure.messaging.spring.sender.SpringRabbitMqMessageSender"));
        assertThat(classes).anyMatch(javaClass ->
                javaClass.getName().equals("org.jfoundry.autoconfigure.messaging.kafka.KafkaMessageSenderAutoConfiguration"));
    }

    @Test
    void frameworkModuleRulesHoldAcrossThePackagedFrameworkModules() {
        FrameworkModuleRules.domain_must_not_depend_on_outer_layers.check(classes);
        FrameworkModuleRules.application_must_not_depend_on_outer_layers.check(classes);
        FrameworkModuleRules.framework_should_use_jmolecules_architecture_annotations_internally.check(classes);
        FrameworkModuleRules.domain_packages_should_be_onion_domain_ring.check(classes);
        FrameworkModuleRules.application_packages_should_be_onion_application_ring.check(classes);
        FrameworkModuleRules.infrastructure_packages_should_be_onion_infrastructure_ring.check(classes);
        FrameworkModuleRules.spring_autoconfigure_packages_should_not_be_onion_rings.check(classes);
        FrameworkModuleRules.infrastructure_must_not_depend_on_spring_autoconfigure.check(classes);
        FrameworkModuleRules.application_store_ports_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.domain_event_dispatcher_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.domain_event_context_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.domain_event_outbox_recorder_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.message_sender_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.payload_serializer_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.externalization_rules_should_not_be_in_messaging_package.check(classes);
        FrameworkModuleRules.event_externalization_rules_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.outbox_dispatcher_should_be_in_application_ring.check(classes);
        FrameworkModuleRules.infrastructure_message_stores_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.spring_application_event_dispatcher_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.spring_event_dispatcher_should_not_be_in_messaging_package.check(classes);
        FrameworkModuleRules.default_domain_event_outbox_recorder_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.outbox_domain_event_dispatcher_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.kafka_message_sender_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.jackson_payload_serializer_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.scheduled_outbox_dispatcher_should_be_in_infrastructure_ring.check(classes);
        FrameworkModuleRules.jobrunr_outbox_dispatcher_should_be_in_infrastructure_ring.check(classes);
    }
}
