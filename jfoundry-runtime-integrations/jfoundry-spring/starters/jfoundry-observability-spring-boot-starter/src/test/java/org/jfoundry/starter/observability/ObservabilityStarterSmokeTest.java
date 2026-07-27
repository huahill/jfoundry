package org.jfoundry.starter.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.jfoundry.application.outbox.OutboxAppendRequest;
import org.jfoundry.application.outbox.OutboxRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.aop.support.AopUtils;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ObservabilityStarterSmokeTest.TestApp.class)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityStarterSmokeTest {

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private CompletedObservations completed;

    @Autowired
    private ApplicationContext context;

    @Test
    void starterObservesOperationWithActuatorProvidedObservationRegistry(CapturedOutput output) {
        assertThat(context.containsBean("jfoundryMicrometerObservationAdvisor")).isTrue();
        assertThat(AopUtils.isAopProxy(recorder)).isTrue();

        recorder.append(OutboxAppendRequest.of(
                "event-secret", "orders.secret", null, "order-confirmed", new Object(), Instant.EPOCH));

        assertThat(completed.contexts).singleElement().satisfies(observation -> {
            assertThat(observation.getName()).isEqualTo("jfoundry.outbox.persist");
            assertThat(observation.getLowCardinalityKeyValue("jfoundry.outcome").getValue())
                    .isEqualTo("success");
        });
        assertThat(output).doesNotContain("not eligible for getting processed by all BeanPostProcessors");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        OutboxRecorder outboxRecorder() {
            return request -> {};
        }

        @Bean
        CompletedObservations completedObservations() {
            return new CompletedObservations();
        }

    }

    static final class CompletedObservations implements ObservationHandler<Observation.Context> {

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
