package org.jfoundry.integration.nativeimage.mybatisplus;

/// Observable result of the MyBatis-Plus Outbox and Inbox Native Image operations.
record NativeMybatisPlusTechnicalStoresResult(boolean outboxClaimed, boolean inboxCompleted) {
}
