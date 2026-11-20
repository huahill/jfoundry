package io.huahill.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.messaging.OutboundMessage;
import io.huahill.jfoundry.application.messaging.SendResult;

@ApplicationScoped
class SuccessfulMessageSender implements MessageSender {

    @Override
    public SendResult send(OutboundMessage message) {
        return SendResult.ok();
    }
}
