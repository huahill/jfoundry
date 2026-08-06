package org.jfoundry.integration.nativeimage.jobrunr;

import java.util.concurrent.atomic.AtomicBoolean;

/// Tracks successful delivery by the test application's message sender.
final class NativeJobRunrPublicationTracker {

    private final AtomicBoolean published = new AtomicBoolean();

    void markPublished() {
        published.set(true);
    }

    boolean published() {
        return published.get();
    }
}
