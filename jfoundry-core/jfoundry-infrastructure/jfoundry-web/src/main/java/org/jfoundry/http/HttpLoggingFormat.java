package org.jfoundry.http;

/// Selects how HTTP diagnostic logs are laid out after the logging level has decided which
/// details to record.
///
/// `INLINE` keeps each event on one `key=value` line. `HUMAN` emits Feign-style records: request
/// lines start with `-->`, response lines start with `<--`, JSON bodies stay on one line, and each
/// side closes with `END HTTP`.
public enum HttpLoggingFormat {

    /// Records each event as one `key=value` line.
    INLINE,

    /// Records Feign-style `-->` request and `<--` response lines, with bodies on one line and `END HTTP` closers.
    HUMAN
}
