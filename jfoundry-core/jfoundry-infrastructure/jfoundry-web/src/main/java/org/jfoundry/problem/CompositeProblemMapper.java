package org.jfoundry.problem;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// Resolves application mappers before JFoundry defaults and a safe final fallback.
/// The localized constructor resolves framework strings and message codes for the supplied locale;
/// the single-argument constructor keeps the built-in English behavior.
public final class CompositeProblemMapper implements ProblemMapper {

    private static final ProblemDescriptor INTERNAL_ERROR = new ProblemDescriptor(
            URI.create("urn:jfoundry:problem:internal-error"), "Internal server error", 500,
            "The server failed to process the request.", Map.of());

    private final List<ProblemMapper> mappers;
    private final ProblemMessageResolver messages;
    private final Supplier<Locale> locale;

    public CompositeProblemMapper(List<? extends ProblemMapper> applicationMappers) {
        this(applicationMappers, ProblemMessageResolver.unresolved(), () -> Locale.ROOT);
    }

    /// Creates a mapper whose JFoundry default mapping resolves localized text for the supplied locale.
    public CompositeProblemMapper(List<? extends ProblemMapper> applicationMappers,
            ProblemMessageResolver messages, Supplier<Locale> locale) {
        Objects.requireNonNull(applicationMappers, "applicationMappers must not be null");
        List<ProblemMapper> ordered = new ArrayList<>(applicationMappers);
        ordered.add(new LocalizedProblemMapper(messages, locale));
        this.mappers = List.copyOf(ordered);
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
    }

    @Override
    public Optional<ProblemDescriptor> map(Exception exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return mappers.stream()
                .map(mapper -> mapper.map(exception))
                .filter(Optional::isPresent)
                .findFirst()
                .orElseGet(() -> Optional.of(internalError()));
    }

    private ProblemDescriptor internalError() {
        Locale currentLocale = locale.get();
        return new ProblemDescriptor(
                INTERNAL_ERROR.type(),
                messages.resolve("problem.http-internal-error.title", currentLocale, List.of())
                        .orElse(INTERNAL_ERROR.title()),
                INTERNAL_ERROR.status(),
                messages.resolve("problem.http-internal-error.detail", currentLocale, List.of())
                        .orElse(INTERNAL_ERROR.detail()),
                Map.of());
    }
}
