package org.jfoundry.application.messaging;

import org.jspecify.annotations.Nullable;

/// Message send result.
/// @param success whether sending succeeded
/// @param errorMessage failure message, or null when sending succeeded
public record SendResult(boolean success, @Nullable String errorMessage) {

    public static SendResult ok() {
        return new SendResult(true, null);
    }

    public static SendResult fail(String errorMessage) {
        return new SendResult(false, errorMessage);
    }
}
