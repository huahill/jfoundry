package org.jfoundry.infrastructure.inbox.helidon;

import jakarta.enterprise.context.Dependent;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonJpaInboxProducerTest {

    @Test
    void usesDependentScopeForNativeCdiCompatibility() {
        assertThat(HelidonJpaInboxProducer.class.isAnnotationPresent(Dependent.class)).isTrue();
    }

    @Test
    void selectsTheBuiltInStrategyForTheDatabaseProduct() {
        assertThat(new HelidonJpaInboxProducer().claimStrategyForProductName("PostgreSQL"))
                .isInstanceOf(JpaInboxClaimStrategies.forProductName("PostgreSQL").getClass());
    }
}
