package org.jfoundry.application.transaction;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-neutral transaction options for application-layer transaction boundaries.
 */
public record TransactionOptions(
        Optional<String> name,
        boolean readOnly,
        Optional<Duration> timeout,
        TransactionPropagation propagation) {

    public TransactionOptions {
        name = Objects.requireNonNull(name, "name must not be null")
                .filter(value -> !value.isBlank());
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        propagation = Objects.requireNonNullElse(propagation, TransactionPropagation.REQUIRED);
        timeout.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("Transaction timeout must be positive");
            }
        });
    }

    public static TransactionOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private @Nullable String name;
        private boolean readOnly;
        private @Nullable Duration timeout;
        private TransactionPropagation propagation = TransactionPropagation.REQUIRED;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder propagation(TransactionPropagation propagation) {
            this.propagation = propagation;
            return this;
        }

        public TransactionOptions build() {
            return new TransactionOptions(Optional.ofNullable(name), readOnly, Optional.ofNullable(timeout), propagation);
        }
    }
}
