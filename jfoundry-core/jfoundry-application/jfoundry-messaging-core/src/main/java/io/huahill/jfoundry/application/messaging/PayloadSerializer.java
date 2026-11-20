package io.huahill.jfoundry.application.messaging;

/// Outbox payload serialization abstraction.
/// <p>
/// Business applications may provide their own implementations, such as Jackson,
/// Gson, or Protobuf. The framework-provided Jackson implementation lives in
/// {@code jfoundry-messaging-jackson}; the core module only keeps the SPI.
public interface PayloadSerializer {

    /// @param event already occurred domain event, usually marked with {@code @Externalized}
    /// @return serialized string, usually JSON, written to the Outbox {@code payload_json} column
    String serialize(Object event);
}
