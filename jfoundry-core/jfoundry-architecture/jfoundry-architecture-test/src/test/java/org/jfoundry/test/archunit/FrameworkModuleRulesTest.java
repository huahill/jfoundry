package org.jfoundry.test.archunit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkModuleRulesTest {

    @Test
    void allFrameworkModuleRulesAreNonNull() {
        assertThat(FrameworkModuleRules.domain_must_not_depend_on_outer_layers).isNotNull();
        assertThat(FrameworkModuleRules.application_must_not_depend_on_outer_layers).isNotNull();
        assertThat(FrameworkModuleRules.framework_should_use_jmolecules_architecture_annotations_internally).isNotNull();
        assertThat(FrameworkModuleRules.domain_packages_should_be_onion_domain_ring).isNotNull();
        assertThat(FrameworkModuleRules.application_packages_should_be_onion_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.infrastructure_packages_should_be_onion_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.spring_autoconfigure_packages_should_not_be_onion_rings).isNotNull();
        assertThat(FrameworkModuleRules.infrastructure_must_not_depend_on_spring_autoconfigure).isNotNull();
        assertThat(FrameworkModuleRules.application_store_ports_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.domain_event_dispatcher_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.domain_event_context_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.domain_event_outbox_recorder_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.message_sender_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.payload_serializer_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.externalization_rules_should_not_be_in_messaging_package).isNotNull();
        assertThat(FrameworkModuleRules.event_externalization_rules_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.outbox_dispatcher_should_be_in_application_ring).isNotNull();
        assertThat(FrameworkModuleRules.infrastructure_message_stores_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.spring_application_event_dispatcher_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.spring_event_dispatcher_should_not_be_in_messaging_package).isNotNull();
        assertThat(FrameworkModuleRules.default_domain_event_outbox_recorder_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.kafka_message_sender_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.jackson_payload_serializer_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.scheduled_outbox_trigger_should_be_in_infrastructure_ring).isNotNull();
        assertThat(FrameworkModuleRules.jobrunr_outbox_trigger_should_be_in_infrastructure_ring).isNotNull();
    }
}
