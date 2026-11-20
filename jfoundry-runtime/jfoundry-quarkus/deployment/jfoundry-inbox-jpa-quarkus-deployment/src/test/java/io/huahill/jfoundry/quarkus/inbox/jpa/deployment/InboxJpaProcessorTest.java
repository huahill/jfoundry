package io.huahill.jfoundry.quarkus.inbox.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.huahill.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageEntity;
import io.huahill.jfoundry.infrastructure.inbox.quarkus.QuarkusJpaInboxProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboxJpaProcessorTest {
    @Test
    void registersTheInboxJpaEntityAndProducer() {
        InboxJpaProcessor processor = new InboxJpaProcessor();
        AdditionalJpaModelBuildItem model = processor.registerInboxJpaModel();
        AdditionalBeanBuildItem beans = processor.registerInboxProducer();
        assertThat(model.getClassName()).isEqualTo(JpaInboxMessageEntity.class.getName());
        assertThat(beans.getBeanClasses()).contains(QuarkusJpaInboxProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
