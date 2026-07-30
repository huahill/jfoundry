package org.jfoundry.integration.nativeimage.jobrunr;

/// Native JobRunr Outbox verification result.
record NativeJobRunrDispatchResult(boolean dispatched, boolean published) {
}
