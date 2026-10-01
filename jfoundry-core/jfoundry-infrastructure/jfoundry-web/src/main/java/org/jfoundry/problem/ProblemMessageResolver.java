package org.jfoundry.problem;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Resolves problem message codes to localized, argument-formatted text.
/// Resolvers are consulted at the HTTP boundary: the request locale selects the translation and
/// unresolved codes fall back to the language-independent exception message.
@FunctionalInterface
public interface ProblemMessageResolver {

    /// Framework catalog base name; bundles ship inside the jfoundry-web jar.
    String FRAMEWORK_BASE_NAME = "jfoundry-problems";

    /// Conventional application catalog base name used by the default resolver chain.
    String APPLICATION_BASE_NAME = "messages";

    /// Returns the formatted text for a message code in the requested locale, or an empty value when unresolved.
    Optional<String> resolve(String code, Locale locale, List<Object> args);

    /// Returns a resolver that never resolves a code; localized lookups fall back to built-in English text.
    static ProblemMessageResolver unresolved() {
        return (code, locale, args) -> Optional.empty();
    }

    /// Returns a resolver backed by the framework catalog only.
    static ProblemMessageResolver framework() {
        return new ResourceBundleProblemMessageResolver(FRAMEWORK_BASE_NAME);
    }

    /// Returns the default resolver chain: the application catalog first, then the framework catalog.
    static ProblemMessageResolver defaults() {
        return new ResourceBundleProblemMessageResolver(APPLICATION_BASE_NAME, FRAMEWORK_BASE_NAME);
    }

    /// Returns a resolver that consults the supplied resolvers in order and uses the first hit.
    static ProblemMessageResolver composite(ProblemMessageResolver... resolvers) {
        List<ProblemMessageResolver> chain = List.of(resolvers);
        return (code, locale, args) -> {
            for (ProblemMessageResolver resolver : chain) {
                Optional<String> resolved = resolver.resolve(code, locale, args);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            return Optional.empty();
        };
    }
}
