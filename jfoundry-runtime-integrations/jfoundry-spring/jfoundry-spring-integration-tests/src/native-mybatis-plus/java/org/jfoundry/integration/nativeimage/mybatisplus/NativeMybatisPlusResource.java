package org.jfoundry.integration.nativeimage.mybatisplus;

import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/// HTTP operation used by the Native Image integration test to exercise MyBatis-Plus persistence.
@RestController
class NativeMybatisPlusResource {

    private final NativeAuditRecordMapper mapper;
    private final OutboxMessageStore outboxStore;
    private final InboxMessageStore inboxStore;

    NativeMybatisPlusResource(NativeAuditRecordMapper mapper,
                              OutboxMessageStore outboxStore,
                              InboxMessageStore inboxStore) {
        this.mapper = mapper;
        this.outboxStore = outboxStore;
        this.inboxStore = inboxStore;
    }

    @GetMapping("/jfoundry/native/mybatis-plus/ready")
    String ready() {
        return "ready";
    }

    @PostMapping("/jfoundry/native/mybatis-plus/audit-record")
    NativeMybatisPlusAuditResult persistAndUpdateAuditRecord() {
        NativeAuditRecord record = new NativeAuditRecord();
        record.setId("native-audit-record");
        record.setContent("created");
        mapper.insert(record);

        NativeAuditRecord persisted = mapper.selectById(record.getId());
        persisted.setContent("updated");
        mapper.updateById(persisted);

        NativeAuditRecord updated = mapper.selectById(record.getId());
        return new NativeMybatisPlusAuditResult(
                updated.getCreatedAt() != null,
                updated.getLastModifiedAt() != null,
                updated.getCreatedBy(),
                updated.getLastModifiedBy(),
                updated.getContent());
    }

    @PostMapping("/jfoundry/native/mybatis-plus/technical-stores")
    NativeMybatisPlusTechnicalStoresResult exerciseTechnicalStores() {
        Instant now = Instant.now();
        OutboxMessage message = OutboxMessage.newPending(
                "native-outbox-event", "native-topic", null, "native.payload", "{}", now);
        outboxStore.append(message);
        boolean outboxClaimed = outboxStore.claimDispatchable(1, "native-image")
                .stream()
                .anyMatch(candidate -> candidate.getEventId().equals(message.getEventId()));

        InboxClaim claim = inboxStore.claim(
                "native-inbox-message", "native-consumer", now, Duration.ofMinutes(1));
        boolean inboxCompleted = claim.acquired() && inboxStore.markProcessed(
                "native-inbox-message", "native-consumer", claim.claimToken(), Instant.now());

        return new NativeMybatisPlusTechnicalStoresResult(outboxClaimed, inboxCompleted);
    }
}
