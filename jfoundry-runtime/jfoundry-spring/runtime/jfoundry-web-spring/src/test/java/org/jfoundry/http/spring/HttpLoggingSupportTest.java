package org.jfoundry.http.spring;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLoggingSupportTest {

    @Test
    void removesQueryAndFragmentWithoutChangingThePath() {
        assertThat(HttpLoggingSupport.withoutQuery(
                URI.create("https://user:password@example.test/orders/42?access_token=secret#fragment")))
                .isEqualTo("https://example.test/orders/42");
    }

    @Test
    void redactsSensitiveHeadersCaseInsensitivelyAndBySecuritySuffix() {
        var headers = new LinkedMultiValueMap<String, String>();
        headers.put("AUTHORIZATION", List.of("Bearer secret"));
        headers.put("X-Service-Token", List.of("token"));
        headers.put("Client.Secret", List.of("secret"));
        headers.put("Vendor-Api-Key", List.of("key"));
        headers.put("Accept", List.of("application/json"));

        var described = HttpLoggingSupport.describeHeaders(headers);

        assertThat(described).containsEntry("AUTHORIZATION", List.of("<redacted>"))
                .containsEntry("X-Service-Token", List.of("<redacted>"))
                .containsEntry("Client.Secret", List.of("<redacted>"))
                .containsEntry("Vendor-Api-Key", List.of("<redacted>"))
                .containsEntry("Accept", List.of("application/json"));
    }

    @Test
    void returnsAnImmutableHeaderSnapshot() {
        var values = new ArrayList<>(List.of("application/json"));
        var headers = new LinkedMultiValueMap<String, String>();
        headers.put("Accept", values);

        var described = HttpLoggingSupport.describeHeaders(headers);
        values.add("text/plain");

        assertThat(described.get("Accept")).containsExactly("application/json");
        assertThatThrownBy(() -> described.put("Other", List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> described.get("Accept").add("other")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void redactsNestedJsonAndArraysWithoutMutatingInputBytes() {
        var body = """
                {"customer":{"password":"secret","name":"Ada"},"items":[{"access-token":"abc"}]}
                """.trim().getBytes(StandardCharsets.UTF_8);
        var original = body.clone();

        var described = HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON, body, true, false);

        assertThat(described).isEqualTo(
                "{\"customer\":{\"password\":\"<redacted>\",\"name\":\"Ada\"},\"items\":[{\"access-token\":\"<redacted>\"}]}");
        assertThat(body).containsExactly(original);
    }

    @Test
    void describesBodiesThatMustNotBeRendered() {
        assertThat(HttpLoggingSupport.describeBody(MediaType.TEXT_PLAIN, "secret".getBytes(), true, false))
                .isEqualTo("<omitted: content-type=text/plain>");
        assertThat(HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON, "{".getBytes(), true, false))
                .isEqualTo("<omitted: invalid JSON>");
        assertThat(HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON, new byte[0], true, false))
                .isEqualTo("<empty>");
        assertThat(HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON, "{}".getBytes(), false, false))
                .isEqualTo("<not fully consumed>");
        assertThat(HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON,
                new byte[HttpLoggingSupport.MAX_BODY_BYTES + 1], true, false))
                .isEqualTo("<truncated at 8192 bytes>");
        assertThat(HttpLoggingSupport.describeBody(MediaType.APPLICATION_JSON, "{}".getBytes(), true, true))
                .isEqualTo("<truncated at 8192 bytes>");
    }

    @Test
    void acceptsStructuredJsonMediaTypes() {
        assertThat(HttpLoggingSupport.isJson(MediaType.parseMediaType("application/problem+json;charset=UTF-8")))
                .isTrue();
    }
}
