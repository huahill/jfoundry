package org.jfoundry.problem;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// Maps JFoundry exceptions through the locale-aware problem catalog.
/// The locale is supplied per lookup so the same mapper instance can serve concurrent requests.
public final class LocalizedProblemMapper implements ProblemMapper {

    private final ProblemMessageResolver messages;
    private final Supplier<Locale> locale;

    public LocalizedProblemMapper(ProblemMessageResolver messages, Supplier<Locale> locale) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
    }

    @Override
    public Optional<ProblemDescriptor> map(Exception exception) {
        try {
            return Optional.of(ProblemCatalog.forException(exception, locale.get(), messages));
        } catch (IllegalArgumentException unsupportedException) {
            return Optional.empty();
        }
    }
}
