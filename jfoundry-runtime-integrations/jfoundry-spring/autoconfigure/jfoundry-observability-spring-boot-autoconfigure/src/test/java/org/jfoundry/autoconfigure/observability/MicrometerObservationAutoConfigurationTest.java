package org.jfoundry.autoconfigure.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.jfoundry.application.outbox.OutboxAppendRequest;
import org.jfoundry.application.outbox.OutboxRecorder;
import org.jfoundry.infrastructure.observability.spring.MicrometerJFoundryObservability;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.util.AopTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerObservationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    MicrometerObservationAutoConfiguration.class));

    @Test
    void observesFrameworkOperationOnItsOriginalSpringBean() {
        ObservationRegistry registry = ObservationRegistry.create();
        CompletedObservations completed = new CompletedObservations();
        registry.observationConfig().observationHandler(completed);

        contextRunner
                .withBean(ObservationRegistry.class, () -> registry)
                .withBean(TestOutboxRecorder.class, TestOutboxRecorder::new)
                .run(context -> {
                    assertThat(context).hasBean("jfoundryMicrometerObservationAdvisor");
                    assertThat(AopUtils.isAopProxy(context.getBean(TestOutboxRecorder.class))).isTrue();
                    context.getBean(OutboxRecorder.class).append(OutboxAppendRequest.of(
                            "event-secret", "orders.secret", null, "order-confirmed", new Object(), Instant.EPOCH));

                    TestOutboxRecorder target = AopTestUtils.getUltimateTargetObject(
                            context.getBean(TestOutboxRecorder.class));
                    assertThat(target.appendCount).isEqualTo(1);
                    assertThat(completed.contexts).singleElement().satisfies(observation -> {
                        assertThat(observation.getName()).isEqualTo("jfoundry.outbox.persist");
                        assertThat(observation.getLowCardinalityKeyValue("jfoundry.outcome").getValue())
                                .isEqualTo("success");
                    });
                });
    }

    @Test
    void backsOffWhenNoFrameworkOperationBeanExists() {
        contextRunner
                .withBean(ObservationRegistry.class, ObservationRegistry::create)
                .run(context -> assertThat(context).doesNotHaveBean("jfoundryMicrometerObservationAdvisor"));
    }

    @Test
    void backsOffWhenActuatorDoesNotProvideAnObservationRegistry() {
        contextRunner
                .withBean(TestOutboxRecorder.class, TestOutboxRecorder::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MicrometerJFoundryObservability.class);
                    assertThat(context).doesNotHaveBean("jfoundryMicrometerObservationAdvisor");
                });
    }

    @Test
    void recordsSuccessForLockCallbacksThatReturnBusinessEnums() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        CompletedObservations completed = new CompletedObservations();
        registry.observationConfig().observationHandler(completed);

        contextRunner
                .withBean(ObservationRegistry.class, () -> registry)
                .withBean(TestLockExecutor.class, TestLockExecutor::new)
                .run(context -> {
                    LockExecutor executor = context.getBean(LockExecutor.class);
                    assertThat(executor.execute(new LockKey("orders", "lock-secret"), LockOptions.defaults(),
                            () -> BusinessOutcome.APPROVED)).isEqualTo(BusinessOutcome.APPROVED);
                    assertThat(completed.contexts).singleElement().satisfies(observation ->
                            assertThat(observation.getLowCardinalityKeyValue("jfoundry.outcome").getValue())
                                    .isEqualTo("success"));
                });
    }

    @Test
    void doesNotObserveAnAlreadyMicrometerObservedOperationTwice() {
        ObservationRegistry registry = ObservationRegistry.create();
        CompletedObservations completed = new CompletedObservations();
        registry.observationConfig().observationHandler(completed);
        OutboxRecorder recorder = new MicrometerJFoundryObservability(registry).observe(new TestOutboxRecorder());

        contextRunner
                .withBean(ObservationRegistry.class, () -> registry)
                .withBean(OutboxRecorder.class, () -> recorder)
                .run(context -> {
                    context.getBean(OutboxRecorder.class).append(OutboxAppendRequest.of(
                            "event-secret", "orders.secret", null, "order-confirmed", new Object(), Instant.EPOCH));
                    assertThat(completed.contexts).singleElement();
                });
    }

    static class TestOutboxRecorder implements OutboxRecorder {

        private int appendCount;

        @Override
        public void append(OutboxAppendRequest request) {
            appendCount++;
        }
    }

    static class TestLockExecutor implements LockExecutor {

        @Override
        public <T> T execute(LockKey key, LockOptions options,
                             org.jfoundry.application.lock.LockCallback<T> callback) throws Exception {
            return callback.execute();
        }
    }

    enum BusinessOutcome {
        APPROVED
    }

    private static final class CompletedObservations implements ObservationHandler<Observation.Context> {

        private final List<Observation.Context> contexts = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            contexts.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
