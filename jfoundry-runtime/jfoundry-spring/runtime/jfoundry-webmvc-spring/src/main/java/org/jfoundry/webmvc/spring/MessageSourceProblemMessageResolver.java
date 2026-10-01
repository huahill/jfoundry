package org.jfoundry.webmvc.spring;

import org.jfoundry.problem.ProblemMessageResolver;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// Bridges Spring's {@link MessageSource} to the runtime-neutral problem message resolver.
/// Message codes fall back to the next resolver in the chain when the MessageSource cannot resolve them.
public final class MessageSourceProblemMessageResolver implements ProblemMessageResolver {

    private final MessageSource messageSource;

    public MessageSourceProblemMessageResolver(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
    }

    @Override
    public Optional<String> resolve(String code, Locale locale, List<Object> args) {
        return Optional.ofNullable(messageSource.getMessage(code, args.toArray(), null, locale));
    }
}
