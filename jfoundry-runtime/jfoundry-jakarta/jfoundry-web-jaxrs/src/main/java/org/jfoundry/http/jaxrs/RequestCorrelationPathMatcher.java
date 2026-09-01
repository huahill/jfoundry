package org.jfoundry.http.jaxrs;

import java.util.List;
import java.util.regex.Pattern;

/// Compiles request-correlation path exclusions using Ant-style wildcards.
public final class RequestCorrelationPathMatcher {

    private RequestCorrelationPathMatcher() {
    }

    /// Returns a predicate matching normalized paths against the supplied patterns.
    public static java.util.function.Predicate<String> predicate(List<String> patterns) {
        var compiled = patterns == null ? List.<Pattern>of() : patterns.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(RequestCorrelationPathMatcher::compile)
                .toList();
        return path -> compiled.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private static Pattern compile(String pattern) {
        var normalized = pattern.startsWith("/") ? pattern : "/" + pattern;
        var regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            var character = normalized.charAt(index);
            if (character == '/' && index + 2 < normalized.length()
                    && normalized.charAt(index + 1) == '*' && normalized.charAt(index + 2) == '*') {
                regex.append("(?:/.*)?");
                index += 2;
            } else if (character == '*' && index + 1 < normalized.length() && normalized.charAt(index + 1) == '*') {
                regex.append(".*");
                index++;
            } else if (character == '*') {
                regex.append("[^/]*");
            } else if (character == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
