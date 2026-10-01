package org.jfoundry.problem;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/// Message resolver backed by classpath ResourceBundles with MessageFormat interpolation.
/// Bundles are consulted in declaration order, so application catalogs take precedence over the framework catalog.
/// Locale fallback follows the requested locale down to the root (default English) bundle without consulting
/// the JVM default locale; a missing bundle is tolerated and reported as an unresolved code.
public final class ResourceBundleProblemMessageResolver implements ProblemMessageResolver {

    private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final List<String> baseNames;

    public ResourceBundleProblemMessageResolver(String... baseNames) {
        this(List.of(baseNames));
    }

    public ResourceBundleProblemMessageResolver(List<String> baseNames) {
        if (baseNames.isEmpty()) {
            throw new IllegalArgumentException("baseNames must not be empty");
        }
        this.baseNames = List.copyOf(baseNames);
    }

    @Override
    public Optional<String> resolve(String code, Locale locale, List<Object> args) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(args, "args must not be null");
        for (String baseName : baseNames) {
            Optional<String> pattern = lookup(baseName, code, locale);
            if (pattern.isPresent()) {
                return Optional.of(format(pattern.get(), locale, args));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> lookup(String baseName, String code, Locale locale) {
        try {
            return Optional.ofNullable(ResourceBundle.getBundle(baseName, locale,
                    ResourceBundleProblemMessageResolver.class.getClassLoader(), NO_DEFAULT_LOCALE_FALLBACK)
                    .getString(code));
        } catch (MissingResourceException unresolved) {
            return Optional.empty();
        }
    }

    private static String format(String pattern, Locale locale, List<Object> args) {
        if (args.isEmpty()) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(args.toArray());
    }
}
