package org.jfoundry.application.exception;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/// Indicates that a use case failed while accessing an external capability through an outbound port.
public class ExternalAccessException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 2547502648268602446L;

    private final @Nullable String publicDetail;

    public ExternalAccessException(String message) {
        super(message);
        this.publicDetail = null;
    }

    public ExternalAccessException(String message, Throwable cause) {
        super(message, cause);
        this.publicDetail = null;
    }

    /// Creates an external-access failure with a reviewed detail that is safe to expose to callers.
    protected ExternalAccessException(String message, Throwable cause, String publicDetail) {
        super(message, cause);
        this.publicDetail = requirePublicDetail(publicDetail);
    }

    /// Returns the reviewed caller-facing detail, or an empty value when the failure must remain masked.
    public Optional<String> publicDetail() {
        return Optional.ofNullable(publicDetail);
    }

    private static String requirePublicDetail(String publicDetail) {
        Objects.requireNonNull(publicDetail, "publicDetail must not be null");
        if (publicDetail.isBlank()) {
            throw new IllegalArgumentException("publicDetail must not be blank");
        }
        return publicDetail;
    }
}
