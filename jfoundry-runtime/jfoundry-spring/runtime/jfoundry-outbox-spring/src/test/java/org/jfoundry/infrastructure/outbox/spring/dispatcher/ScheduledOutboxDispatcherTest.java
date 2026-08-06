package org.jfoundry.infrastructure.outbox.spring.dispatcher;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledOutboxDispatcherTest {

    private OutboxMessageStore repository;
    private MessageSender messageSender;
    private BackoffStrategy backoff;
    private ScheduledOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxMessageStore.class);
        messageSender = mock(MessageSender.class);
        backoff = failedAttempts -> Duration.ofSeconds(1);
        dispatcher = new ScheduledOutboxDispatcher(repository, messageSender, 5, backoff, 5);
    }

    @Test
    void marksAsPublishedOnSendSuccess() {
        OutboxMessage message = message("evt-1");
        when(repository.claimDispatchable(eq(5), any())).thenReturn(List.of(message));
        when(messageSender.send(any())).thenReturn(SendResult.ok());

        dispatcher.dispatch(5);

        verify(repository).markAsPublished("evt-1", "claim-evt-1");
        verify(repository, never()).markAsFailed(any(), any(), any(), anyInt(), any());
    }

    @Test
    void marksAsFailedOnSendFailure() {
        OutboxMessage message = message("evt-1");
        when(repository.claimDispatchable(eq(5), any())).thenReturn(List.of(message));
        when(messageSender.send(any())).thenReturn(SendResult.fail("conn refused"));

        dispatcher.dispatch(5);

        verify(repository).markAsFailed(eq("evt-1"), eq("claim-evt-1"),
                eq("conn refused"), eq(5), same(backoff));
        verify(repository, never()).markAsPublished(any(), any());
    }

    @Test
    void marksAsFailedOnSendException() {
        OutboxMessage message = message("evt-1");
        when(repository.claimDispatchable(eq(5), any())).thenReturn(List.of(message));
        when(messageSender.send(any())).thenThrow(new RuntimeException("kafka down"));

        dispatcher.dispatch(5);

        verify(repository).markAsFailed(eq("evt-1"), eq("claim-evt-1"),
                contains("kafka down"), eq(5), same(backoff));
    }

    @Test
    void singleFailureDoesNotBlockRemainingMessages() {
        OutboxMessage first = message("evt-1");
        OutboxMessage second = message("evt-2");
        when(repository.claimDispatchable(eq(5), any())).thenReturn(List.of(first, second));
        when(messageSender.send(org.mockito.ArgumentMatchers.argThat(outbound ->
                outbound != null && outbound.topic().equals("topic")
                        && outbound.payload().equals("payload-evt-1"))))
                .thenThrow(new RuntimeException("fail"));
        when(messageSender.send(org.mockito.ArgumentMatchers.argThat(outbound ->
                outbound != null && outbound.topic().equals("topic")
                        && outbound.payload().equals("payload-evt-2"))))
                .thenReturn(SendResult.ok());

        dispatcher.dispatch(5);

        verify(repository).markAsFailed(eq("evt-1"), eq("claim-evt-1"), any(), eq(5), any());
        verify(repository).markAsPublished("evt-2", "claim-evt-2");
    }

    @Test
    void passesPayloadKeyToSender() {
        OutboxMessage message = message("evt-1");
        message.setPayloadKey("key-A");
        when(repository.claimDispatchable(eq(5), any())).thenReturn(List.of(message));
        when(messageSender.send(any())).thenReturn(SendResult.ok());

        dispatcher.dispatch(5);

        verify(messageSender).send(org.mockito.ArgumentMatchers.argThat(outbound ->
                outbound.topic().equals("topic") && outbound.payloadKey().equals("key-A")
                        && outbound.payload().equals("payload-evt-1")));
    }

    private OutboxMessage message(String eventId) {
        OutboxMessage message = OutboxMessage.newPending(
                eventId, "topic", null, "com.example.Foo", "payload-" + eventId, Instant.now());
        message.setClaimToken("claim-" + eventId);
        return message;
    }
}
