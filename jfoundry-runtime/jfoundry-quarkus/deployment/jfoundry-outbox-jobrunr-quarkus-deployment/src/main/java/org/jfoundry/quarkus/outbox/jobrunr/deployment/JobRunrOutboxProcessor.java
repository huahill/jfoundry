package org.jfoundry.quarkus.outbox.jobrunr.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.jfoundry.infrastructure.outbox.jobrunr.quarkus.QuarkusJobRunrOutboxProducer;

/// Registers the JobRunr Outbox producer, native reflection, and SQL migration resources.
class JobRunrOutboxProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerJobRunrOutboxBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusJobRunrOutboxProducer.class)
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .setUnremovable()
                .build();
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem indexJobRunrTypes() {
        return new AdditionalIndexedClassesBuildItem(
                JobRunrOutboxTrigger.class.getName(),
                OutboxDispatchJobRequest.class.getName());
    }

    @BuildStep
    ReflectiveClassBuildItem registerJobRunrTypes() {
        return ReflectiveClassBuildItem.builder(JobRunrOutboxTrigger.class, OutboxDispatchJobRequest.class)
                .constructors()
                .methods()
                .fields()
                .build();
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem registerJobRunrSqlMigrations() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("org/jobrunr/storage/sql/**/*.sql")
                .build();
    }
}

