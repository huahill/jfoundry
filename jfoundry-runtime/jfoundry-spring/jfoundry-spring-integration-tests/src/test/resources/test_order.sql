DROP TABLE IF EXISTS test_order;
CREATE TABLE test_order (
    id              VARCHAR(64)   NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    amount          INT           NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS versioned_order;
CREATE TABLE versioned_order (
    id              VARCHAR(64)   NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    amount          INT           NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
