package org.jfoundry.application.exception;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Base exception for expected application-use-case failures.
 */
public abstract class ApplicationException extends RuntimeException {

    private final @Nullable String problemCode;
    private final List<Object> problemArguments;

    protected ApplicationException(String message) {
        super(message);
        this.problemCode = null;
        this.problemArguments = List.of();
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
        this.problemCode = null;
        this.problemArguments = List.of();
    }

    /// Creates an expected application failure identified by a stable message code with interpolation arguments.
    /// The log message stays locale-independent ("{@code code [args]}"); caller-facing text is resolved from
    /// message catalogs at the HTTP boundary, never at the throw site.
    protected ApplicationException(String code, Object... args) {
        super(codeMessage(code, args));
        this.problemCode = requireCode(code);
        this.problemArguments = requireValidArguments(args);
    }

    /// Returns the stable message code of this failure, or an empty value when it was created with a literal message.
    public Optional<String> problemCode() {
        return Optional.ofNullable(problemCode);
    }

    /// Returns the interpolation arguments for the message code; empty when no code is present.
    public List<Object> problemArguments() {
        return problemArguments;
    }

    static String codeMessage(String code, Object[] args) {
        return args.length == 0 ? requireCode(code) : requireCode(code) + " " + Arrays.deepToString(args);
    }

    static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("problem code must not be null or blank");
        }
        return code;
    }

    static List<Object> requireValidArguments(Object[] args) {
        for (Object arg : args) {
            if (arg == null || !(arg instanceof String || arg instanceof Number || arg instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "problem arguments must be non-null String, Number, or Boolean values but got: " + arg);
            }
        }
        return List.of(args);
    }
}
