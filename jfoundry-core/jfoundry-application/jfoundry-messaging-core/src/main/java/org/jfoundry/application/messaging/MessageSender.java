package org.jfoundry.application.messaging;

/// Outbound message sending abstraction.
public interface MessageSender {

    /// @param message outbound transport envelope
    /// @return send result
    SendResult send(OutboundMessage message);
}
