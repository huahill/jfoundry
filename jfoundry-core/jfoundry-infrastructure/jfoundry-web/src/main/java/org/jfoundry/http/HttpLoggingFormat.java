package org.jfoundry.http;

/// Selects how HTTP diagnostic logs are laid out after the logging level has decided which
/// details to record.
///
/// `INLINE` keeps each event on one `key=value` line. `HUMAN` emits Feign-style records: one log
/// line per header or JSON line, so a console shows a wrapped exchange without stuffing newlines
/// into a single message.
public enum HttpLoggingFormat {

    /// Records each event as one `key=value` line.
    INLINE,

    /// Records one log line per request line, header, or pretty-printed JSON line.
    HUMAN
}
