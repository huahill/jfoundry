package org.jfoundry.application.event.externalization;

import org.jmolecules.event.annotation.Externalized;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalizationRuleResolverTest {

    private final ExternalizationRuleResolver resolver = new ExternalizationRuleResolver();

    @Externalized("env.created")
    static class EnvCreatedEvent implements DomainEvent {
    }

    @Externalized("")
    @MessageRouting(topic = "env.app.install")
    static class EnvAppInstallEvent implements DomainEvent {
        @SuppressWarnings("unused")
        public String getEnvAppId() { return "app-1"; }
    }

    @Externalized("env.app.uninstall")
    @MessageRouting(topic = "env.app.uninstall", key = "#this.envAppId")
    static class EnvAppUninstallEvent implements DomainEvent {
        @SuppressWarnings("unused")
        public String getEnvAppId() { return "app-2"; }
    }

    @Externalized("")
    static class BarelyExternalizedWithoutTopic implements DomainEvent {
    }

    static class NotExternalized implements DomainEvent {
    }

    @MessageRouting(topic = "orphan.topic")
    static class OrphanRoutingOnly implements DomainEvent {
    }

    @Test
    void resolvesTopicFromExternalizedValueWhenNoMessageRouting() {
        Optional<ExternalizationRule> rule = resolver.resolve(new EnvCreatedEvent());

        assertThat(rule).isPresent();
        assertThat(rule.get().topic()).isEqualTo("env.created");
        assertThat(rule.get().payloadKey()).isNull();
    }

    @Test
    void messageRoutingOverridesExternalizedValue() {
        Optional<ExternalizationRule> rule = resolver.resolve(new EnvAppInstallEvent());

        assertThat(rule).isPresent();
        assertThat(rule.get().topic()).isEqualTo("env.app.install");
        assertThat(rule.get().payloadKey()).isNull();
    }

    @Test
    void messageRoutingKeyPropertyPathIsEvaluated() {
        Optional<ExternalizationRule> rule = resolver.resolve(new EnvAppUninstallEvent());

        assertThat(rule).isPresent();
        assertThat(rule.get().payloadKey()).isEqualTo("app-2");
    }

    @Test
    void emptyExternalizedValueFailsFast() {
        assertThatThrownBy(() -> resolver.resolve(new BarelyExternalizedWithoutTopic()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void notExternalizedReturnsEmpty() {
        assertThat(resolver.resolve(new NotExternalized())).isEmpty();
    }

    @Test
    void orphanMessageRoutingWithoutExternalizedReturnsEmpty() {
        // WARN log emitted; events not externalized.
        assertThat(resolver.resolve(new OrphanRoutingOnly())).isEmpty();
    }

    @Test
    void invalidKeyPropertyPathDegradesToNull() {
        // Use a separate test fixture
        @Externalized("topic")
        @MessageRouting(topic = "topic", key = "#this.nonexistent")
        class BadSpelEvent implements DomainEvent {
        }
        Optional<ExternalizationRule> rule = resolver.resolve(new BadSpelEvent());

        assertThat(rule).isPresent();
        assertThat(rule.get().payloadKey()).isNull();
    }
}
