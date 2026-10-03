package org.jfoundry.quarkus.outbox.jobrunr.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.jfoundry.infrastructure.outbox.jobrunr.quarkus.QuarkusJobRunrOutboxProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobRunrOutboxProcessorTest {

    @Test
    void registersTheProducerAsAnUnremovableApplicationBean() {
        AdditionalBeanBuildItem beans = new JobRunrOutboxProcessor().registerJobRunrOutboxBeans();

        assertThat(beans.getBeanClasses()).contains(QuarkusJobRunrOutboxProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }

    @Test
    void indexesTheJobRunrOutboxTypes() {
        AdditionalIndexedClassesBuildItem indexed = new JobRunrOutboxProcessor().indexJobRunrTypes();

        assertThat(indexed.getClassesToIndex()).contains(
                JobRunrOutboxTrigger.class.getName(),
                OutboxDispatchJobRequest.class.getName());
    }

    @Test
    void registersTheJobRunrOutboxTypesForReflection() {
        ReflectiveClassBuildItem reflection = new JobRunrOutboxProcessor().registerJobRunrTypes();

        assertThat(reflection.getClassNames()).contains(
                JobRunrOutboxTrigger.class.getName(),
                OutboxDispatchJobRequest.class.getName());
        assertThat(reflection.isConstructors()).isTrue();
        assertThat(reflection.isMethods()).isTrue();
        assertThat(reflection.isFields()).isTrue();
    }
}
