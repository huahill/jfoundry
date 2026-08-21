package org.jfoundry.problem;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Converts portable Jakarta Validation violations to caller-facing request validation errors.
public final class JakartaRequestValidationErrors {

    private JakartaRequestValidationErrors() {
    }

    /// Converts violations, rendering paths only for proven JSON request-document violations.
    public static List<RequestValidationProblem.Error> from(
            Iterable<? extends ConstraintViolation<?>> violations,
            Predicate<? super ConstraintViolation<?>> requestDocumentViolation) {
        Objects.requireNonNull(violations, "violations must not be null");
        Objects.requireNonNull(requestDocumentViolation, "requestDocumentViolation must not be null");

        List<RequestValidationProblem.Error> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            Objects.requireNonNull(violation, "violations must not contain null");
            List<String> path = requestDocumentViolation.test(violation)
                    ? requestDocumentPath(violation)
                    : List.of();
            String detail = violation.getMessage() == null ? "is invalid" : violation.getMessage();
            errors.add(path.isEmpty()
                    ? RequestValidationProblem.Error.forRequest(detail)
                    : RequestValidationProblem.Error.atPath(path, detail));
        }
        return errors.stream()
                .sorted(Comparator.comparing((RequestValidationProblem.Error error) -> String.join("/", error.path()))
                        .thenComparing(RequestValidationProblem.Error::detail))
                .toList();
    }

    private static List<String> requestDocumentPath(ConstraintViolation<?> violation) {
        List<String> path = new ArrayList<>();
        boolean parameterReached = false;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER) {
                parameterReached = true;
                continue;
            }
            if (!parameterReached
                    || (node.getKind() != ElementKind.PROPERTY
                    && node.getKind() != ElementKind.CONTAINER_ELEMENT)) {
                continue;
            }
            appendIterableLocation(path, node);
            if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                path.add(node.getName());
            }
        }
        return List.copyOf(path);
    }

    private static void appendIterableLocation(List<String> path, Path.Node node) {
        if (!node.isInIterable()) {
            return;
        }
        if (node.getIndex() != null) {
            path.add(node.getIndex().toString());
        } else if (node.getKey() != null) {
            path.add(node.getKey().toString());
        }
    }
}
