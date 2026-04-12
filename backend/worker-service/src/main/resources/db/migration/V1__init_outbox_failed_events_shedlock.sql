CREATE TABLE outbox_events (
    id            BIGSERIAL    PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    processed_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE failed_events (
    id               BIGSERIAL    PRIMARY KEY,
    topic_or_task    VARCHAR(100) NOT NULL,
    payload          JSONB,
    error_reason     TEXT,
    retry_count      INT          NOT NULL DEFAULT 0,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_outbox_events_status  ON outbox_events(status);
CREATE INDEX idx_failed_events_status  ON failed_events(status);

