package io.huahill.jfoundry.application.outbox;

import java.time.Duration;

/// Retry backoff strategy abstraction.
/// <p>
/// The {@code failedAttempts} argument is a zero-based count of already failed
/// attempts:
/// <ul>
///   <li>first failure, caller passes 0: delay = base</li>
///   <li>second failure, caller passes 1: delay = base * 2</li>
/// </ul>
public interface BackoffStrategy {

    /// @param failedAttempts already failed attempts, zero-based
    /// @return delay before the next retry
    Duration nextDelay(int failedAttempts);
}
