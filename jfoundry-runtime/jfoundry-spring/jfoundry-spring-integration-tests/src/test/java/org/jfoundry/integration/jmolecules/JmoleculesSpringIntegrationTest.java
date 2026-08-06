package org.jfoundry.integration.jmolecules;

import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies that jfoundry relies on jMolecules Spring's own converter auto-configuration
/// instead of duplicating Identifier and Association converters.
class JmoleculesSpringIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Test
    void jmoleculesSpringConvertersAreRegisteredByUpstreamAutoConfiguration() {
        try (AssertableApplicationContext context = AssertableApplicationContext.get(() -> {
            AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
            ctx.setServletContext(new MockServletContext());
            ctx.setEnvironment(new MockEnvironment()
                    .withProperty("spring.autoconfigure.exclude",
                            "org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration"));
            ctx.register(TestApp.class);
            ctx.refresh();
            return ctx;
        })) {

            ConversionService conversionService = context.getBean(ConversionService.class);

            assertThat(conversionService.canConvert(String.class, TestOrderId.class)).isTrue();
            assertThat(conversionService.canConvert(TestOrderId.class, String.class)).isTrue();

            TestOrderId orderId = conversionService.convert("order-1", TestOrderId.class);

            assertThat(orderId).isEqualTo(new TestOrderId("order-1"));
            assertThat(conversionService.convert(orderId, String.class)).isEqualTo("order-1");
        }
    }

    record TestOrderId(String value) implements Identifier {
    }
}
