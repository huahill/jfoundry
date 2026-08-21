package org.jfoundry.problem;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Defines the shared RFC 9457 representation for request validation failures.
public final class RequestValidationProblem {

    public static final URI TYPE = URI.create("urn:jfoundry:problem:request-validation");
    static final Comparator<Error> ERROR_ORDER = Comparator
            .comparing(Error::path, RequestValidationProblem::comparePaths)
            .thenComparing(Error::detail);

    private RequestValidationProblem() {
    }

    /// Creates a request validation problem from caller-facing validation errors.
    public static ProblemDescriptor create(List<? extends Error> errors) {
        Objects.requireNonNull(errors, "errors must not be null");
        List<Map<String, String>> renderedErrors = errors.stream()
                .map(error -> Objects.requireNonNull(error, "errors must not contain null"))
                .sorted(ERROR_ORDER)
                .map(Error::toExtension)
                .toList();
        return new ProblemDescriptor(
                TYPE,
                "Request validation failed",
                400,
                "The request failed validation. See 'errors' for details.",
                Map.of("errors", renderedErrors));
    }

    /// Describes one validation error and its optional location in the request document.
    public record Error(List<String> path, String detail) {

        public Error {
            path = List.copyOf(Objects.requireNonNull(path, "path must not be null"));
            if (path.stream().anyMatch(Objects::isNull)) {
                throw new NullPointerException("path tokens must not be null");
            }
            Objects.requireNonNull(detail, "detail must not be null");
        }

        /// Creates an error for a specific JSON document path.
        public static Error atPath(List<String> path, String detail) {
            if (Objects.requireNonNull(path, "path must not be null").isEmpty()) {
                throw new IllegalArgumentException("path must not be empty");
            }
            return new Error(path, detail);
        }

        /// Creates an error that has no reliable location in the request document.
        public static Error forRequest(String detail) {
            return new Error(List.of(), detail);
        }

        private Map<String, String> toExtension() {
            Map<String, String> extension = new LinkedHashMap<>();
            extension.put("detail", detail);
            if (!path.isEmpty()) {
                extension.put("pointer", jsonPointer(path));
            }
            return Collections.unmodifiableMap(extension);
        }
    }

    private static String jsonPointer(List<String> path) {
        StringBuilder pointer = new StringBuilder();
        for (String token : path) {
            pointer.append('/')
                    .append(token.replace("~", "~0").replace("/", "~1"));
        }
        try {
            return new URI(null, null, pointer.toString()).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("path cannot be rendered as a JSON Pointer URI fragment", exception);
        }
    }

    private static int comparePaths(List<String> left, List<String> right) {
        int commonLength = Math.min(left.size(), right.size());
        for (int index = 0; index < commonLength; index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
