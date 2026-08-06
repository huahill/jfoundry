package org.jfoundry.infrastructure.transaction.spring;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Spring {@link TransactionTemplate}-based adapter for {@link TransactionRunner}.
 */
public class SpringTransactionRunner implements TransactionRunner {

    private final PlatformTransactionManager transactionManager;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    }

    @Override
    public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(options.readOnly());
        template.setPropagationBehavior(toSpringPropagation(options.propagation()));
        options.name().ifPresent(template::setName);
        options.timeout().ifPresent(timeout -> template.setTimeout(Math.toIntExact(timeout.toSeconds())));

        try {
            return template.execute(status -> {
                try {
                    return callback.execute();
                } catch (Exception ex) {
                    status.setRollbackOnly();
                    throw new TransactionCallbackException(ex);
                }
            });
        } catch (TransactionCallbackException ex) {
            throw ex.original;
        }
    }

    private static int toSpringPropagation(TransactionPropagation propagation) {
        return switch (propagation) {
            case REQUIRED -> TransactionDefinition.PROPAGATION_REQUIRED;
            case REQUIRES_NEW -> TransactionDefinition.PROPAGATION_REQUIRES_NEW;
            case SUPPORTS -> TransactionDefinition.PROPAGATION_SUPPORTS;
            case MANDATORY -> TransactionDefinition.PROPAGATION_MANDATORY;
            case NOT_SUPPORTED -> TransactionDefinition.PROPAGATION_NOT_SUPPORTED;
            case NEVER -> TransactionDefinition.PROPAGATION_NEVER;
        };
    }

    private static final class TransactionCallbackException extends RuntimeException {

        private final Exception original;

        private TransactionCallbackException(Exception original) {
            super(original);
            this.original = original;
        }
    }
}
