package org.jfoundry.application.exception;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Indicates that a use case failed while accessing an external capability through an outbound port.
public class ExternalAccessException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 2547502648268602446L;

    private final @Nullable String publicDetail;
    private final @Nullable String publicDetailCode;
    private final List<Object> publicDetailArguments;

    public ExternalAccessException(String message) {
        super(message);
        this.publicDetail = null;
        this.publicDetailCode = null;
        this.publicDetailArguments = List.of();
    }

    public ExternalAccessException(String message, Throwable cause) {
        super(message, cause);
        this.publicDetail = null;
        this.publicDetailCode = null;
        this.publicDetailArguments = List.of();
    }

    /// Creates an external-access failure with a reviewed detail that is safe to expose to callers.
    protected ExternalAccessException(String message, Throwable cause, String publicDetail) {
        super(message, cause);
        this.publicDetail = requirePublicDetail(publicDetail);
        this.publicDetailCode = null;
        this.publicDetailArguments = List.of();
    }

    /// Creates an external-access failure whose caller-facing detail is resolved from a reviewed message code.
    /// The private message stays internal; the code is resolved to caller-facing text at the HTTP boundary.
    protected ExternalAccessException(String message, Throwable cause, String publicDetailCode,
            Object... publicDetailArgs) {
        super(message, cause);
        this.publicDetail = null;
        this.publicDetailCode = requireCode(publicDetailCode);
        this.publicDetailArguments = requireValidArguments(publicDetailArgs);
    }

    /// Returns the reviewed caller-facing detail, or an empty value when the failure must remain masked.
    public Optional<String> publicDetail() {
        return Optional.ofNullable(publicDetail);
    }

    /// Returns the reviewed caller-facing detail code, or an empty value when no code-based detail was provided.
    public Optional<String> publicDetailCode() {
        return Optional.ofNullable(publicDetailCode);
    }

    /// Returns the interpolation arguments for the caller-facing detail code; empty when no code is present.
    public List<Object> publicDetailArguments() {
        return publicDetailArguments;
    }

    private static String requirePublicDetail(String publicDetail) {
        Objects.requireNonNull(publicDetail, "publicDetail must not be null");
        if (publicDetail.isBlank()) {
            throw new IllegalArgumentException("publicDetail must not be blank");
        }
        return publicDetail;
    }
}
