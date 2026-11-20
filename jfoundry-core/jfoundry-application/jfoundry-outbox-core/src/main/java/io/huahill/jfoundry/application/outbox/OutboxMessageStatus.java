package io.huahill.jfoundry.application.outbox;

/// Outbox entry status.
/// <p>
/// State transitions:
/// <ul>
///   <li>{@code PENDING} -> {@code DISPATCHING} by atomic claim -> {@code PUBLISHED}</li>
///   <li>{@code DISPATCHING} -> {@code FAILED} on delivery failure -> {@code DEAD_LETTERED}</li>
///   <li>stuck {@code DISPATCHING} -> {@code PENDING} by recovery</li>
///   <li>{@code DEAD_LETTERED} -> {@code PENDING} by reactivation</li>
/// </ul>
public enum OutboxMessageStatus {
    PENDING,
    DISPATCHING,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED
}
