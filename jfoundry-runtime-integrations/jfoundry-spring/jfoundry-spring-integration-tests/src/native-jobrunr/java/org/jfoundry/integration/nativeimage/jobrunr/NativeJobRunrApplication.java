package org.jfoundry.integration.nativeimage.jobrunr;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.OutboxAppendRequest;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.application.transaction.TransactionRunner;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.Map;

/// Native Image consumer used to certify JFoundry JobRunr Outbox dispatching.
@SpringBootApplication
public class NativeJobRunrApplication {

    static final String EVENT_ID = "native-jobrunr-event";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NativeJobRunrApplication.class);
        application.setDefaultProperties(Map.of("spring.jpa.hibernate.ddl-auto", "create-drop"));
        application.run(args);
    }

    @Bean
    NativeJobRunrPublicationTracker nativeJobRunrPublicationTracker() {
        return new NativeJobRunrPublicationTracker();
    }

    @Bean
    MessageSender nativeJobRunrMessageSender(NativeJobRunrPublicationTracker publicationTracker) {
        return outbound -> {
            publicationTracker.markPublished();
            return SendResult.ok();
        };
    }

    @Bean
    ApplicationRunner appendNativeJobRunrOutboxMessage(
            OutboxTemplate outboxTemplate, TransactionRunner transactionRunner) {
        return ignored -> transactionRunner.run(() -> outboxTemplate.append(OutboxAppendRequest.of(
                EVENT_ID,
                "jfoundry.native.jobrunr",
                EVENT_ID,
                NativeJobRunrEvent.class.getName(),
                new NativeJobRunrEvent(EVENT_ID),
                Instant.now())));
    }
}
