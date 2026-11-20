package org.jfoundry.http;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLoggingPolicyTest {

    @Test
    void removesCredentialsQueryAndFragmentFromUris() {
        assertThat(HttpLoggingPolicy.withoutQuery(
                URI.create("https://user:secret@example.test:8443/orders/42?access_token=secret#fragment")))
                .isEqualTo("https://example.test:8443/orders/42");
    }

    @Test
    void describesOnlyTheDefaultDiagnosticHeadersAndRedactsSensitiveValues() {
        var headers = new LinkedHashMap<String, List<?>>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("Content-Type", List.of("application/json"));
        headers.put("User-Agent", List.of("Mozilla/5.0"));
        headers.put("X-Trace-Id", List.of(42));

        var described = HttpLoggingPolicy.describeHeaders(headers);

        assertThat(described).containsEntry("Authorization", List.of("<redacted>"))
                .containsEntry("Content-Type", List.of("application/json"))
                .doesNotContainKeys("User-Agent", "X-Trace-Id");
        assertThatThrownBy(() -> described.put("Other", List.of("value")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void configuredIncludedHeadersReplaceTheDefaultAndWildcardIncludesEveryHeader() {
        var headers = new LinkedHashMap<String, List<?>>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("User-Agent", List.of("Mozilla/5.0"));
        headers.put("X-Trace-Id", List.of("trace-1"));

        assertThat(HttpLoggingPolicy.describeHeaders(headers, List.of("user-agent", "x-trace-id")))
                .containsOnlyKeys("User-Agent", "X-Trace-Id")
                .containsEntry("User-Agent", List.of("Mozilla/5.0"))
                .containsEntry("X-Trace-Id", List.of("trace-1"));
        assertThat(HttpLoggingPolicy.describeHeaders(headers, List.of("*")))
                .containsEntry("Authorization", List.of("<redacted>"))
                .containsEntry("User-Agent", List.of("Mozilla/5.0"))
                .containsEntry("X-Trace-Id", List.of("trace-1"));
        assertThat(HttpLoggingPolicy.describeHeaders(headers, List.of())).isEmpty();
    }

    @Test
    void recognizesRuntimeIndependentSensitiveNames() {
        assertThat(HttpLoggingPolicy.isSensitiveHeader("X-Client-Secret")).isTrue();
        assertThat(HttpLoggingPolicy.isSensitiveHeader("tenant.credentials")).isTrue();
        assertThat(HttpLoggingPolicy.isSensitiveJsonField("nestedPasswordValue")).isTrue();
        assertThat(HttpLoggingPolicy.isSensitiveJsonField("refresh-token")).isTrue();
        assertThat(HttpLoggingPolicy.isSensitiveJsonField("displayName")).isFalse();
    }

    @Test
    void acceptsBodyByteLimitsWithinTheSupportedRangeOnly() {
        assertThat(HttpLoggingPolicy.maxBodyBytes(1)).isEqualTo(1);
        assertThat(HttpLoggingPolicy.maxBodyBytes(HttpLoggingPolicy.DEFAULT_MAX_BODY_BYTES)).isEqualTo(8_192);
        assertThat(HttpLoggingPolicy.maxBodyBytes(HttpLoggingPolicy.ABSOLUTE_MAX_BODY_BYTES)).isEqualTo(65_536);

        assertThatThrownBy(() -> HttpLoggingPolicy.maxBodyBytes(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBodyBytes must be between 1 and " + HttpLoggingPolicy.ABSOLUTE_MAX_BODY_BYTES);
        assertThatThrownBy(() -> HttpLoggingPolicy.maxBodyBytes(HttpLoggingPolicy.ABSOLUTE_MAX_BODY_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpLoggingPolicy.maxBodyBytes(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
