package org.jfoundry.application.exception;

/**
 * Indicates that a use case conflicts with the current application state.
 */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Creates a conflict failure identified by a stable message code with interpolation arguments.
    /// The code is resolved to caller-facing text at the HTTP boundary through message catalogs.
    public ConflictException(String code, Object... args) {
        super(code, args);
    }
}
