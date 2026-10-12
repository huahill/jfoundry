package org.jfoundry.web.spring.client;

import org.jfoundry.application.exception.ExternalAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// Indicates a non-success HTTP response and retains its original body.
public final class HttpResponseException extends ExternalAccessException {

    private static final int MAX_MESSAGE_BODY_BYTES = 400;

    private static final int MAX_MESSAGE_BODY_CHARS = 200;

    private final int statusCode;

    private final byte[] body;

    /// Creates a failure for an error response that has no body.
    public HttpResponseException(HttpStatusCode statusCode) {
        this(statusCode, new byte[0], null);
    }

    /// Creates a failure that encapsulates the original response body.
    ///
    /// The retained bytes are copied and are neither parsed nor redacted. The diagnostic message appends the raw
    /// decoded text when the body is shorter than 400 bytes. A longer body contributes only its first 200 characters
    /// and its total length. The preview is not a caller-facing problem detail.
    public HttpResponseException(HttpStatusCode statusCode, byte[] body, MediaType contentType) {
        super(message(statusCode, body, contentType));
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode must not be null").value();
        this.body = Objects.requireNonNull(body, "body must not be null").clone();
    }

    /// Returns the HTTP status code.
    public int statusCode() {
        return this.statusCode;
    }

    /// Returns a copy of the original response body.
    public byte[] body() {
        return this.body.clone();
    }

    private static String message(HttpStatusCode statusCode, byte[] body, MediaType contentType) {
        var status = "HTTP response failed with status "
                + Objects.requireNonNull(statusCode, "statusCode must not be null").value();
        Objects.requireNonNull(body, "body must not be null");
        if (body.length == 0) {
            return status;
        }
        return status + "\n" + preview(body, charset(contentType));
    }

    private static String preview(byte[] body, Charset charset) {
        if (body.length < MAX_MESSAGE_BODY_BYTES) {
            return new String(body, charset);
        }
        var buffer = new char[MAX_MESSAGE_BODY_CHARS];
        var offset = 0;
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(body), charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE))) {
            while (offset < buffer.length) {
                var read = reader.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        } catch (IOException exception) {
            return "... (" + body.length + " bytes)";
        }
        return new String(buffer, 0, offset) + "... (" + body.length + " bytes)";
    }

    private static Charset charset(MediaType contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            var resolved = contentType.getCharset();
            return resolved == null ? StandardCharsets.UTF_8 : resolved;
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.UTF_8;
        }
    }
}
