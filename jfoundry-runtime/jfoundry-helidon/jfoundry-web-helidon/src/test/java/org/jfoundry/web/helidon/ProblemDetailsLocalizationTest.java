package org.jfoundry.web.helidon;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsLocalizationTest {

    @Test
    void localizesProblemResponsesForThePreferredRequestLocale() {
        ProblemDetailsExceptionMappers.DomainRuleViolationMapper mapper =
                new ProblemDetailsExceptionMappers.DomainRuleViolationMapper();
        mapper.requestHeaders = stubHttpHeaders(List.of(Locale.SIMPLIFIED_CHINESE));

        Response response = mapper.toResponse(new DomainRuleViolationException("order.quota-exceeded", 2));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("zh-CN");
        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getString("title")).isEqualTo("违反领域规则");
        assertThat(problem.getString("detail")).isEqualTo("order.quota-exceeded [2]");
        assertThat(problem.getString("code")).isEqualTo("order.quota-exceeded");
        assertThat(problem.getJsonArray("args").size()).isEqualTo(1);
    }

    @Test
    void keepsEnglishResponsesWithoutContentLanguageWhenNoLocaleIsPreferred() {
        ProblemDetailsExceptionMappers.DomainRuleViolationMapper mapper =
                new ProblemDetailsExceptionMappers.DomainRuleViolationMapper();

        Response response = mapper.toResponse(new DomainRuleViolationException("order.quota-exceeded", 2));

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_LANGUAGE)).isNull();
        JsonObject problem = (JsonObject) response.getEntity();
        assertThat(problem.getString("title")).isEqualTo("Domain rule violation");
        assertThat(problem.getString("detail")).isEqualTo("order.quota-exceeded [2]");
    }

    @Test
    void resolvesTheRequestLocaleFromAcceptableLanguages() {
        assertThat(ProblemDetailsResponses.requestLocale(null)).isEqualTo(Locale.ROOT);
        assertThat(ProblemDetailsResponses.requestLocale(stubHttpHeaders(List.of()))).isEqualTo(Locale.ROOT);
        assertThat(ProblemDetailsResponses.requestLocale(stubHttpHeaders(List.of(Locale.SIMPLIFIED_CHINESE))))
                .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    private static HttpHeaders stubHttpHeaders(List<Locale> acceptableLanguages) {
        return new HttpHeaders() {

            @Override
            public List<Locale> getAcceptableLanguages() {
                return acceptableLanguages;
            }

            @Override
            public List<MediaType> getAcceptableMediaTypes() {
                return List.of();
            }

            @Override
            public Map<String, Cookie> getCookies() {
                return Map.of();
            }

            @Override
            public Date getDate() {
                return null;
            }

            @Override
            public int getLength() {
                return -1;
            }

            @Override
            public Locale getLanguage() {
                return null;
            }

            @Override
            public MediaType getMediaType() {
                return null;
            }

            @Override
            public List<String> getRequestHeader(String name) {
                return null;
            }

            @Override
            public String getHeaderString(String name) {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getRequestHeaders() {
                return new MultivaluedHashMap<>();
            }
        };
    }
}
