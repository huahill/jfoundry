package org.jfoundry.application.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// A structured distributed-lock key with a non-sensitive scope and an opaque value.
public record LockKey(String scope, String value) {

    public LockKey {
        requireText(scope, "scope");
        requireText(value, "value");
    }

    /// Returns the stable backend name without exposing the lock value.
    public String backendName() {
        return "jfoundry-lock:v1:" + sha256(scope + "\u0000" + value);
    }

    @Override
    public String toString() {
        return "LockKey[scope=" + scope + "]";
    }

    private static void requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
