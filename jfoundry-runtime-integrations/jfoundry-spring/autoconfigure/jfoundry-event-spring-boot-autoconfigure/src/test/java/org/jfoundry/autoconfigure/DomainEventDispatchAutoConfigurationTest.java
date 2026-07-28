package org.jfoundry.autoconfigure;

import org.jfoundry.application.event.CompositeDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.autoconfigure.event.DomainEventDispatchAutoConfiguration;
import org.jfoundry.autoconfigure.aop.JFoundryAopAutoConfiguration;
import org.jfoundry.autoconfigure.event.DomainEventDispatchInterceptor;
import org.jfoundry.autoconfigure.event.DomainEventPersistenceAutoConfiguration;
import org.jfoundry.autoconfigure.event.DomainEventScope;
import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.jmolecules.ddd.types.Identifier;
import org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher;
import org.jfoundry.infrastructure.outbox.spring.externalization.OutboxDomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventDispatchAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JFoundryAopAutoConfiguration.class,
                    DomainEventPersistenceAutoConfiguration.class,
                    DomainEventDispatchAutoConfiguration.class))
            .withBean(ApplicationEventPublisher.class, () -> event -> {
            });

    @Test
    void defaultConfigurationRegistersContextAndSpringDispatcher() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DomainEventScope.class);
            assertThat(context).hasSingleBean(DomainEventContext.class);
            assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
            assertThat(context).hasSingleBean(CompositeDomainEventDispatcher.class);
            assertThat(context.getBean(DomainEventDispatcher.class)).isInstanceOf(CompositeDomainEventDispatcher.class);
            assertThat(context).hasSingleBean(DomainEventDispatchInterceptor.class);
            assertThat(context).hasBean("domainEventDispatchAdvisor");
            assertThat(context.getBean("domainEventDispatchAdvisor")).isInstanceOf(Advisor.class);
        });
    }

    @Test
    void disabledDispatchKeepsContextButDoesNotRegisterDispatchInfrastructure() {
        contextRunner
                .withPropertyValues("jfoundry.domain.event.dispatch.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainEventScope.class);
                    assertThat(context).hasSingleBean(DomainEventContext.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatchInterceptor.class);
                    assertThat(context).doesNotHaveBean("domainEventDispatchAdvisor");
                });
    }

    @Test
    void legacyDomainEventEnabledPropertyDoesNotDisableDispatchInfrastructure() {
        contextRunner
                .withPropertyValues("jfoundry.domain.event.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainEventScope.class);
                    assertThat(context).hasSingleBean(DomainEventContext.class);
                    assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
                    assertThat(context.getBean(DomainEventDispatcher.class)).isInstanceOf(CompositeDomainEventDispatcher.class);
                    assertThat(context).hasSingleBean(DomainEventDispatchInterceptor.class);
                    assertThat(context).hasBean("domainEventDispatchAdvisor");
                });
    }

    @Test
    void missingSpringEventModuleDoesNotBreakMinimalStarter() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.jfoundry.infrastructure.event.spring"))
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainEventScope.class);
                    assertThat(context).hasSingleBean(DomainEventContext.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatchInterceptor.class);
                    assertThat(context).doesNotHaveBean("domainEventDispatchAdvisor");
                });
    }

    @Test
    void missingPersistenceModuleDoesNotBreakEventDispatch() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.jfoundry.infrastructure.persistence"))
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainEventScope.class);
                    assertThat(context).hasSingleBean(DomainEventContext.class);
                    assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
                    assertThat(context).doesNotHaveBean("domainEventContextBeanPostProcessorRegistrar");
                });
    }

    @Test
    void disabledSpringDispatcherDoesNotRegisterSpringPublisher() {
        contextRunner
                .withPropertyValues("jfoundry.domain.event.dispatch.spring.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DomainEventContext.class);
                    assertThat(context).doesNotHaveBean(SpringApplicationEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(DomainEventDispatchInterceptor.class);
                });
    }

    @Test
    void enabledOutboxDispatcherParticipatesInCompositeDispatcher() {
        contextRunner
                .withUserConfiguration(OutboxRecorderConfiguration.class)
                .withPropertyValues("jfoundry.domain.event.dispatch.outbox.enabled=true")
                .run(context -> {
                    TestOutboxRecorder recorder = context.getBean(TestOutboxRecorder.class);
                    DomainEventDispatcher dispatcher = context.getBean(DomainEventDispatcher.class);

                    dispatcher.dispatch(List.of(new TestEvent("order-1")));

                    assertThat(context).hasSingleBean(SpringApplicationEventDispatcher.class);
                    assertThat(context).hasSingleBean(OutboxDomainEventDispatcher.class);
                    assertThat(dispatcher).isInstanceOf(CompositeDomainEventDispatcher.class);
                    assertThat(recorder.recordedEvents).extracting(TestEvent::id).containsExactly("order-1");
                });
    }

    @Test
    void injectsDomainEventContextIntoPersistenceRepositories() {
        contextRunner
                .withUserConfiguration(PersistenceRepositoryConfiguration.class)
                .run(context -> {
                    TestPersistenceRepository repository = context.getBean(TestPersistenceRepository.class);
                    RecordingDomainEventContext domainEventContext = context.getBean(RecordingDomainEventContext.class);
                    TestAggregate aggregate = TestAggregate.create("order-1");

                    repository.add(aggregate);

                    assertThat(domainEventContext.registeredAggregates).containsExactly(aggregate);
                });
    }

    @Test
    void aggregateRepositoryLifecycleMethodsRemainAdvisableWithClassBasedProxies() {
        AtomicInteger interceptedCalls = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new TestPersistenceRepository());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodBeforeAdvice) (method, args, target) -> {
            if (method.getName().equals("add")) {
                interceptedCalls.incrementAndGet();
            }
        });
        TestPersistenceRepository repository = (TestPersistenceRepository) proxyFactory.getProxy();

        repository.add(TestAggregate.create("order-1"));

        assertThat(interceptedCalls).hasValue(1);
    }

    @Configuration
    static class OutboxRecorderConfiguration {

        @Bean
        TestOutboxRecorder testOutboxRecorder() {
            return new TestOutboxRecorder();
        }
    }

    static final class TestOutboxRecorder implements DomainEventOutboxRecorder {

        private final List<TestEvent> recordedEvents = new ArrayList<>();

        @Override
        public void record(List<? extends DomainEvent> events) {
            for (DomainEvent event : events) {
                recordedEvents.add((TestEvent) event);
            }
        }
    }

    record TestEvent(String id) implements DomainEvent {
    }

    @Configuration
    static class PersistenceRepositoryConfiguration {

        @Bean
        RecordingDomainEventContext recordingDomainEventContext() {
            return new RecordingDomainEventContext();
        }

        @Bean
        TestPersistenceRepository testPersistenceRepository() {
            return new TestPersistenceRepository();
        }
    }

    static final class RecordingDomainEventContext implements DomainEventContext {

        private final List<EventRecordable> registeredAggregates = new ArrayList<>();

        @Override
        public void register(EventRecordable aggregate) {
            registeredAggregates.add(aggregate);
        }
    }

    static class TestPersistenceRepository
            extends AbstractAggregateRepository<TestAggregate, TestAggregateId> {

        @Override
        protected TestAggregate doFindById(TestAggregateId id) {
            return null;
        }

        @Override
        protected void doAdd(TestAggregate aggregate) {
        }

        @Override
        protected void doModify(TestAggregate aggregate) {
        }

        @Override
        protected void doRemove(TestAggregate aggregate) {
        }
    }

    static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }

        static TestAggregate create(String id) {
            return new TestAggregate(new TestAggregateId(id));
        }
    }

    record TestAggregateId(String value) implements Identifier, Serializable {
    }

}
