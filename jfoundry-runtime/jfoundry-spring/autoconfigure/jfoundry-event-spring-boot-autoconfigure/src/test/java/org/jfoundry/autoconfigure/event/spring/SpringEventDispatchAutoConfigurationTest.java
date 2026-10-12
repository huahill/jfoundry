package org.jfoundry.autoconfigure.event.spring;

import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.autoconfigure.event.DomainEventDispatchAutoConfiguration;
import org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventDispatchAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DomainEventDispatchAutoConfiguration.class,
                    SpringEventDispatchAutoConfiguration.class))
            .withBean(ApplicationEventPublisher.class, () -> event -> {
            });

    @Test
    void registersSpringDispatcherBeforeDomainEventDispatch() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
            assertThat(context).hasSingleBean(DomainEventDispatchCoordinator.class);
            assertThat(context).hasBean("domainEventDispatchAdvisor");
        });
    }

    @Test
    void applicationDispatcherReplacesTheAutoConfiguredDispatcher() {
        contextRunner
                .withUserConfiguration(ApplicationDispatcherConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
                    assertThat(context.getBean(SpringApplicationEventDispatcher.class))
                            .isInstanceOf(ApplicationSpringDispatcher.class);
                });
    }

    @Configuration
    static class ApplicationDispatcherConfiguration {

        @Bean
        ApplicationSpringDispatcher springApplicationEventDispatcher() {
            return new ApplicationSpringDispatcher(event -> {
            });
        }
    }

    static final class ApplicationSpringDispatcher extends SpringApplicationEventDispatcher {

        ApplicationSpringDispatcher(ApplicationEventPublisher eventPublisher) {
            super(eventPublisher);
        }
    }
}
