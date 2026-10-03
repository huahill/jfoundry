package org.jfoundry.infrastructure.outbox.jobrunr.quarkus;

import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.JobRunrOutboxTrigger;
import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;

/// Registers the JobRunr Outbox handler and its recurring job when
/// {@code jfoundry.outbox.dispatcher.mode=jobrunr}.
///
/// The built-in Quarkus scheduler does not dispatch in that mode. JobRunr must stay an explicit
/// extension; this type is absent unless the application selects it.
@IfBuildProperty(name = "jfoundry.outbox.dispatcher.mode", stringValue = "jobrunr")
public final class QuarkusJobRunrOutboxProducer {

    static final String RECURRING_JOB_ID = "jfoundry-outbox-dispatch";

    private final String cron;
    private final int batchSize;

    @Inject
    public QuarkusJobRunrOutboxProducer(
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.cron", defaultValue = "*/10 * * * * *")
            String cron,
            @ConfigProperty(name = "jfoundry.outbox.dispatcher.batch-size", defaultValue = "50")
            int batchSize) {
        this.cron = cron;
        this.batchSize = batchSize;
    }

    /// Creates the CDI handler JobRunr resolves for each recurring Outbox request.
    @Produces
    @ApplicationScoped
    @Unremovable
    public JobRunrOutboxTrigger jobRunrOutboxTrigger(OutboxDispatcher dispatcher) {
        return new JobRunrOutboxTrigger(dispatcher, batchSize);
    }

    void registerRecurringJob(@Observes StartupEvent event, Instance<JobRequestScheduler> schedulers) {
        if (schedulers.isResolvable()) {
            registerRecurringJob(schedulers.get());
        }
    }

    void registerRecurringJob(JobRequestScheduler scheduler) {
        scheduler.scheduleRecurrently(RECURRING_JOB_ID, cron, new OutboxDispatchJobRequest());
    }
}
