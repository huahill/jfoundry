package org.jfoundry.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Resolves application mappers before JFoundry defaults and a safe final fallback.
public final class CompositeProblemMapper implements ProblemMapper {

    private static final ProblemDescriptor INTERNAL_ERROR = new ProblemDescriptor(
            java.net.URI.create("urn:jfoundry:problem:internal-error"), "Internal server error", 500,
            "The server failed to process the request.", java.util.Map.of());

    private final List<ProblemMapper> mappers;

    public CompositeProblemMapper(List<? extends ProblemMapper> applicationMappers) {
        Objects.requireNonNull(applicationMappers, "applicationMappers must not be null");
        List<ProblemMapper> ordered = new ArrayList<>(applicationMappers);
        ordered.add(exception -> {
            try {
                return Optional.of(ProblemCatalog.forException(exception));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
        this.mappers = List.copyOf(ordered);
    }

    @Override
    public Optional<ProblemDescriptor> map(Exception exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return mappers.stream()
                .map(mapper -> mapper.map(exception))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.of(INTERNAL_ERROR));
    }
}
