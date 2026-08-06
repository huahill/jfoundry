package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class OutboxMaintenanceConditions {

    private static final String DISPATCHER_MODE = "jfoundry.outbox.dispatcher.mode";
    private static final String DISPATCHER_MODE_SCHEDULED = "scheduled";
    private static final String DISPATCHER_MODE_JOBRUNR = "jobrunr";
    private static final String RECOVERY_ENABLED = "jfoundry.outbox.recovery.enabled";
    private static final String CLEANUP_ENABLED = "jfoundry.outbox.cleanup.enabled";

    private OutboxMaintenanceConditions() {
    }

    static final class SchedulingEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return isScheduledDispatcherMode(environment)
                    || maintenanceEnabled(environment, RECOVERY_ENABLED)
                    || maintenanceEnabled(environment, CLEANUP_ENABLED);
        }
    }

    static final class RecoveryEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return maintenanceEnabled(context.getEnvironment(), RECOVERY_ENABLED);
        }
    }

    static final class CleanupEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return maintenanceEnabled(context.getEnvironment(), CLEANUP_ENABLED);
        }
    }

    private static boolean maintenanceEnabled(Environment environment, String propertyName) {
        if (!isManagedDispatcherMode(environment)) {
            return false;
        }
        return Boolean.parseBoolean(environment.getProperty(propertyName, "true"));
    }

    private static boolean isScheduledDispatcherMode(Environment environment) {
        return DISPATCHER_MODE_SCHEDULED.equalsIgnoreCase(dispatcherMode(environment));
    }

    private static boolean isManagedDispatcherMode(Environment environment) {
        String mode = dispatcherMode(environment);
        return DISPATCHER_MODE_SCHEDULED.equalsIgnoreCase(mode)
                || DISPATCHER_MODE_JOBRUNR.equalsIgnoreCase(mode);
    }

    private static String dispatcherMode(Environment environment) {
        return environment.getProperty(DISPATCHER_MODE, DISPATCHER_MODE_SCHEDULED);
    }
}
