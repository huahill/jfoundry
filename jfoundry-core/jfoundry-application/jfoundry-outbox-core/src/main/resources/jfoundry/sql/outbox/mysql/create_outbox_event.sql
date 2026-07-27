CREATE TABLE jfoundry_outbox_event (
    event_id        VARCHAR(64)   NOT NULL,
    topic           VARCHAR(255)  NOT NULL,
    payload_key     VARCHAR(255),
    payload_type    VARCHAR(500)  NOT NULL,
    payload_json    MEDIUMTEXT    NOT NULL,  -- 16MB; supports large event payloads
    traceparent     VARCHAR(512),
    tracestate      VARCHAR(512),
    aggregate_type   VARCHAR(255),
    aggregate_id     VARCHAR(255),
    aggregate_version BIGINT,
    -- PENDING / DISPATCHING / PUBLISHED / FAILED / DEAD_LETTERED
    status          VARCHAR(32)   NOT NULL,
    retry_count     INT           NOT NULL DEFAULT 0,
    error_message   VARCHAR(2000),
    occurred_at     TIMESTAMP     NOT NULL,
    last_attempt_at TIMESTAMP,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    -- Atomic claim columns used by dispatchers while records are DISPATCHING.
    claimed_at      TIMESTAMP,
    claimed_by      VARCHAR(100),
    -- Unique token generated per claim batch; read-back matches this token
    -- exactly so stale DISPATCHING records from previous batches are excluded.
    claim_token     VARCHAR(36),
    PRIMARY KEY (event_id)
);
CREATE INDEX idx_outbox_status_retry ON jfoundry_outbox_event (status, next_retry_at);
-- Composite index for dispatcher claim predicates.
CREATE INDEX idx_outbox_claim ON jfoundry_outbox_event (status, claimed_at);
CREATE INDEX idx_outbox_cleanup ON jfoundry_outbox_event (status, occurred_at, event_id);
-- Lookup records claimed by the current claim batch.
CREATE INDEX idx_outbox_claim_token ON jfoundry_outbox_event (claim_token);
CREATE INDEX idx_outbox_aggregate ON jfoundry_outbox_event (aggregate_type, aggregate_id, aggregate_version);
