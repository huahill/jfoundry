package org.jfoundry.webmvc.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSourceProblemMessageResolverTest {

    private final StaticMessageSource messageSource = new StaticMessageSource();
    private final MessageSourceProblemMessageResolver resolver = new MessageSourceProblemMessageResolver(messageSource);

    @Test
    void resolvesAndFormatsCodesThroughTheMessageSource() {
        messageSource.addMessage("order.quota-exceeded", Locale.SIMPLIFIED_CHINESE, "配额已超出:当前 {0}");

        assertThat(resolver.resolve("order.quota-exceeded", Locale.SIMPLIFIED_CHINESE, List.of(2)))
                .contains("配额已超出:当前 2");
    }

    @Test
    void reportsUnresolvedCodesAsEmpty() {
        assertThat(resolver.resolve("unknown.code", Locale.ENGLISH, List.of())).isEmpty();
    }
}
