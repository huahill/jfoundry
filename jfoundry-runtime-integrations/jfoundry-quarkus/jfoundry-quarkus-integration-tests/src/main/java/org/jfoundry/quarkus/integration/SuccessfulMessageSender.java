package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;

@ApplicationScoped
class SuccessfulMessageSender implements MessageSender {

    @Override
    public SendResult send(String topic, String payloadKey, String payload) {
        return SendResult.ok();
    }
}
