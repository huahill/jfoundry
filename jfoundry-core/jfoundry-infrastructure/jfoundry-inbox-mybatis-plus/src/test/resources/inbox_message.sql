DROP TABLE IF EXISTS jfoundry_inbox_message;
CREATE TABLE jfoundry_inbox_message (
    id            VARCHAR(64)   NOT NULL,
    message_id    VARCHAR(128)  NOT NULL,
    consumer_name VARCHAR(255)  NOT NULL,
    status        VARCHAR(32)   NOT NULL,
    processed_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    claimed_at    TIMESTAMP,
    claim_token   VARCHAR(36),
    error_message VARCHAR(2000),
    PRIMARY KEY (id),
    CONSTRAINT uk_inbox_consumer_message UNIQUE (consumer_name, message_id)
);
CREATE INDEX idx_inbox_processed_at ON jfoundry_inbox_message (processed_at);
CREATE INDEX idx_inbox_claim ON jfoundry_inbox_message (status, claimed_at);
