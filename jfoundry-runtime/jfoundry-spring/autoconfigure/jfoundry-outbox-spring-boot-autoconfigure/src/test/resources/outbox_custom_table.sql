-- Additional custom_outbox table for OutboxTableNameOverrideTest.
-- Schema mirrors jfoundry_outbox_event (see outbox_event.sql) so the TableNameHandler
-- redirect is a pure name swap — no column/typing drift.
CREATE TABLE IF NOT EXISTS custom_outbox (
    event_id        VARCHAR(64)   NOT NULL,
    topic           VARCHAR(255)  NOT NULL,
    payload_key     VARCHAR(255),
    payload_type    VARCHAR(500)  NOT NULL,
    payload_json    TEXT          NOT NULL,
    traceparent     VARCHAR(512),
    tracestate      VARCHAR(512),
    aggregate_type   VARCHAR(255),
    aggregate_id     VARCHAR(255),
    aggregate_version BIGINT,
    status          VARCHAR(32)   NOT NULL,
    retry_count     INT           NOT NULL DEFAULT 0,
    error_message   VARCHAR(2000),
    occurred_at     TIMESTAMP     NOT NULL,
    last_attempt_at TIMESTAMP,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    claimed_at      TIMESTAMP,
    claimed_by      VARCHAR(100),
    -- Mirror jfoundry_outbox_event schema, including claim_token.
    claim_token     VARCHAR(36),
    PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS idx_custom_outbox_status_retry ON custom_outbox (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_custom_outbox_claim ON custom_outbox (status, claimed_at);
CREATE INDEX IF NOT EXISTS idx_custom_outbox_claim_token ON custom_outbox (claim_token);
CREATE INDEX IF NOT EXISTS idx_custom_outbox_aggregate ON custom_outbox (aggregate_type, aggregate_id, aggregate_version);
