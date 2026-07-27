package org.jfoundry.application.messaging;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSenderTest {

    @Test
    void sendsOneStructuredOutboundMessage() {
        AtomicReference<OutboundMessage> sent = new AtomicReference<>();
        MessageSender sender = messageToSend -> {
            sent.set(messageToSend);
            return SendResult.ok();
        };
        OutboundMessage message = OutboundMessage.of("orders", "order-42", "{}");

        SendResult result = sender.send(message);

        assertThat(result).isEqualTo(SendResult.ok());
        assertThat(sent.get()).isSameAs(message);
    }
}
