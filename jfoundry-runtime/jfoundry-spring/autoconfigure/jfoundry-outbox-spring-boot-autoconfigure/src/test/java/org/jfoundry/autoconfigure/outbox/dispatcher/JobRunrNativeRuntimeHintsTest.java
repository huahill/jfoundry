package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class JobRunrNativeRuntimeHintsTest {

    @Test
    void registersSqlMigrationsRequiredByJobRunrNativeImageStartup() {
        RuntimeHints hints = new RuntimeHints();

        new JobRunrNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource()
                .forResource("org/jobrunr/storage/sql/common/migrations/v000__create_migrations_table.sql")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.resource()
                .forResource("org/jobrunr/storage/sql/postgres/migrations/v014__improve_job_stats.sql")
                .test(hints)).isTrue();
    }
}
