package org.jfoundry.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingLevelTest {

    @Test
    void exposesTheExpectedDetailBoundaries() {
        assertThat(HttpLoggingLevel.NONE.includesHeaders()).isFalse();
        assertThat(HttpLoggingLevel.NONE.includesBodies()).isFalse();
        assertThat(HttpLoggingLevel.BASIC.includesHeaders()).isFalse();
        assertThat(HttpLoggingLevel.BASIC.includesBodies()).isFalse();
        assertThat(HttpLoggingLevel.HEADERS.includesHeaders()).isTrue();
        assertThat(HttpLoggingLevel.HEADERS.includesBodies()).isFalse();
        assertThat(HttpLoggingLevel.FULL.includesHeaders()).isTrue();
        assertThat(HttpLoggingLevel.FULL.includesBodies()).isTrue();
    }
}
