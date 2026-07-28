package org.jfoundry.autoconfigure.observability;

import io.micrometer.observation.ObservationRegistry;
import org.jfoundry.application.inbox.InboxMessageProcessor;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxRecorder;
import org.jfoundry.infrastructure.observability.spring.MicrometerJFoundryObservability;
import org.jfoundry.infrastructure.observability.spring.MicrometerObservationAdvisor;
import org.jfoundry.infrastructure.observability.spring.MicrometerObservationInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Role;

/// Auto-configures Micrometer Observation for JFoundry Spring operation beans.
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration",
        "org.jfoundry.autoconfigure.event.DomainEventOutboxRecorderAutoConfiguration",
        "org.jfoundry.autoconfigure.inbox.InboxAutoConfiguration",
        "org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration",
        "org.jfoundry.autoconfigure.lock.DistributedLockAutoConfiguration"
})
@ConditionalOnClass({ObservationRegistry.class, MicrometerJFoundryObservability.class, MicrometerObservationAdvisor.class})
@ConditionalOnBean(ObservationRegistry.class)
public class MicrometerObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MicrometerJFoundryObservability micrometerJFoundryObservability(ObservationRegistry observationRegistry) {
        return new MicrometerJFoundryObservability(observationRegistry);
    }

    @Bean(name = MicrometerObservationAdvisor.INTERCEPTOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = MicrometerObservationAdvisor.INTERCEPTOR_BEAN_NAME)
    public MicrometerObservationInterceptor micrometerObservationInterceptor(
            MicrometerJFoundryObservability observability) {
        return new MicrometerObservationInterceptor(observability);
    }

    @Bean(name = "jfoundryMicrometerObservationAdvisor")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnBean({ObservationRegistry.class, MicrometerObservationInterceptor.class})
    @ConditionalOnMissingBean(name = "jfoundryMicrometerObservationAdvisor")
    @Conditional(FrameworkOperationBeanCondition.class)
    public static MicrometerObservationAdvisor jfoundryMicrometerObservationAdvisor() {
        return new MicrometerObservationAdvisor();
    }

    static final class FrameworkOperationBeanCondition extends AnyNestedCondition {

        FrameworkOperationBeanCondition() {
            super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(OutboxRecorder.class)
        static final class OutboxRecorderAvailable {
        }

        @ConditionalOnBean(OutboxDispatcher.class)
        static final class OutboxDispatcherAvailable {
        }

        @ConditionalOnBean(InboxMessageProcessor.class)
        static final class InboxMessageProcessorAvailable {
        }

        @ConditionalOnBean(LockExecutor.class)
        static final class LockExecutorAvailable {
        }
    }
}
