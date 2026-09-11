package org.jfoundry.test.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.jfoundry", importOptions = ImportOption.DoNotIncludeTests.class)
class FrameworkArchitectureSelfTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
            FrameworkModuleRules.domain_must_not_depend_on_outer_layers;

    @ArchTest
    static final ArchRule application_must_not_depend_on_outer_layers =
            FrameworkModuleRules.application_must_not_depend_on_outer_layers;

    @ArchTest
    static final ArchRule framework_should_use_jmolecules_architecture_annotations_internally =
            FrameworkModuleRules.framework_should_use_jmolecules_architecture_annotations_internally;

    @ArchTest
    static final ArchRule domain_packages_should_be_onion_domain_ring =
            FrameworkModuleRules.domain_packages_should_be_onion_domain_ring;

    @ArchTest
    static final ArchRule application_packages_should_be_onion_application_ring =
            FrameworkModuleRules.application_packages_should_be_onion_application_ring;

    @ArchTest
    static final ArchRule infrastructure_packages_should_be_onion_infrastructure_ring =
            FrameworkModuleRules.infrastructure_packages_should_be_onion_infrastructure_ring;

    @ArchTest
    static final ArchRule spring_autoconfigure_packages_should_not_be_onion_rings =
            FrameworkModuleRules.spring_autoconfigure_packages_should_not_be_onion_rings;

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_spring_autoconfigure =
            FrameworkModuleRules.infrastructure_must_not_depend_on_spring_autoconfigure;

    @ArchTest
    static final ArchRule application_store_ports_should_be_in_application_ring =
            FrameworkModuleRules.application_store_ports_should_be_in_application_ring;

    @ArchTest
    static final ArchRule domain_event_dispatcher_should_be_in_application_ring =
            FrameworkModuleRules.domain_event_dispatcher_should_be_in_application_ring;

    @ArchTest
    static final ArchRule domain_event_context_should_be_in_application_ring =
            FrameworkModuleRules.domain_event_context_should_be_in_application_ring;

    @ArchTest
    static final ArchRule domain_event_outbox_recorder_should_be_in_application_ring =
            FrameworkModuleRules.domain_event_outbox_recorder_should_be_in_application_ring;

    @ArchTest
    static final ArchRule message_sender_should_be_in_application_ring =
            FrameworkModuleRules.message_sender_should_be_in_application_ring;

    @ArchTest
    static final ArchRule payload_serializer_should_be_in_application_ring =
            FrameworkModuleRules.payload_serializer_should_be_in_application_ring;

    @ArchTest
    static final ArchRule externalization_rules_should_not_be_in_messaging_package =
            FrameworkModuleRules.externalization_rules_should_not_be_in_messaging_package;

    @ArchTest
    static final ArchRule event_externalization_rules_should_be_in_application_ring =
            FrameworkModuleRules.event_externalization_rules_should_be_in_application_ring;

    @ArchTest
    static final ArchRule outbox_dispatcher_should_be_in_application_ring =
            FrameworkModuleRules.outbox_dispatcher_should_be_in_application_ring;

    @ArchTest
    static final ArchRule infrastructure_message_stores_should_be_in_infrastructure_ring =
            FrameworkModuleRules.infrastructure_message_stores_should_be_in_infrastructure_ring;

    @ArchTest
    static final ArchRule spring_application_event_dispatcher_should_be_in_infrastructure_ring =
            FrameworkModuleRules.spring_application_event_dispatcher_should_be_in_infrastructure_ring;

    @ArchTest
    static final ArchRule spring_event_dispatcher_should_not_be_in_messaging_package =
            FrameworkModuleRules.spring_event_dispatcher_should_not_be_in_messaging_package;

    @ArchTest
    static final ArchRule default_domain_event_outbox_recorder_should_be_in_application_ring =
            FrameworkModuleRules.default_domain_event_outbox_recorder_should_be_in_application_ring;

    @ArchTest
    static final ArchRule kafka_message_sender_should_be_in_infrastructure_ring =
            FrameworkModuleRules.kafka_message_sender_should_be_in_infrastructure_ring;

    @ArchTest
    static final ArchRule jackson_payload_serializer_should_be_in_infrastructure_ring =
            FrameworkModuleRules.jackson_payload_serializer_should_be_in_infrastructure_ring;

    @ArchTest
    static final ArchRule scheduled_outbox_trigger_should_be_in_infrastructure_ring =
            FrameworkModuleRules.scheduled_outbox_trigger_should_be_in_infrastructure_ring;

    @ArchTest
    static final ArchRule jobrunr_outbox_trigger_should_be_in_infrastructure_ring =
            FrameworkModuleRules.jobrunr_outbox_trigger_should_be_in_infrastructure_ring;
}
