package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;

@ApplicationScoped
class SuccessfulMessageSender implements MessageSender {

    @Override
    public SendResult send(OutboundMessage message) {
        return SendResult.ok();
    }
}
