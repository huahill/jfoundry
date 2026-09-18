package org.jfoundry.problem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceBundleProblemMessageResolverTest {

    private final ResourceBundleProblemMessageResolver resolver =
            new ResourceBundleProblemMessageResolver("test-problems", "missing-test-bundle");

    @Test
    void resolvesPatternsFromTheBestLocaleMatchAndFormatsArguments() {
        assertThat(resolver.resolve("test.greeting", Locale.SIMPLIFIED_CHINESE, List.of("世界")))
                .contains("你好 世界");
    }

    @Test
    void fallsBackToTheRootBundleForMissingTranslationsWithoutConsultingTheJvmDefaultLocale() {
        assertThat(resolver.resolve("test.plain", Locale.SIMPLIFIED_CHINESE, List.of()))
                .contains("Hello");
        assertThat(resolver.resolve("test.greeting", Locale.ENGLISH, List.of("world")))
                .contains("Hello world");
    }

    @Test
    void treatsMissingBundlesAndMissingCodesAsUnresolved() {
        ResourceBundleProblemMessageResolver missingOnly = new ResourceBundleProblemMessageResolver("no-such-bundle");

        assertThat(missingOnly.resolve("test.greeting", Locale.ENGLISH, List.of("world"))).isEmpty();
        assertThat(resolver.resolve("test.unknown", Locale.ENGLISH, List.of())).isEmpty();
    }

    @Test
    void returnsPatternsVerbatimWhenNoArgumentsAreSupplied() {
        assertThat(resolver.resolve("test.plain", Locale.ENGLISH, List.of())).contains("Hello");
    }

    @Test
    void rejectsEmptyBaseNameLists() {
        assertThatThrownBy(() -> new ResourceBundleProblemMessageResolver(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
