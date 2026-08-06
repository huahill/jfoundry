package org.jfoundry.autoconfigure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSenderFallbackAbsenceTest {

    @Test
    void doesNotPublishALoggingMessageSenderFallback() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent(
                "org.jfoundry.infrastructure.messaging.spring.sender.LoggingMessageSender", classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(
                "org.jfoundry.autoconfigure.messaging.MessageSenderAutoConfiguration", classLoader)).isFalse();
    }
}
