package org.jfoundry.application.outbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/// Derives unique Outbox event ids for mapped integration destinations.
///
/// Mapped rows keep the source event id as the origin prefix and append a stable
/// 16-character SHA-256 suffix of {@code payloadType + "\0" + topic}. The result
/// fits {@code event_id VARCHAR(64)} for UUID-sized source identifiers.
final class MappedOutboxEventIds {

    private MappedOutboxEventIds() {
    }

    static String derive(String sourceEventId, String payloadType, String topic) {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(payloadType, "payloadType must not be null");
        Objects.requireNonNull(topic, "topic must not be null");
        byte[] hash = sha256((payloadType + '\0' + topic).getBytes(StandardCharsets.UTF_8));
        return sourceEventId + ':' + HexFormat.of().formatHex(hash, 0, 8);
    }

    private static byte[] sha256(byte[] material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for mapped Outbox event ids", ex);
        }
    }
}
