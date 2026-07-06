-- H2 variant of V20260707100001__add_domain_outbox.sql (JSON→CLOB, DATETIME→TIMESTAMP).
-- 领域事件 outbox（at-least-once 投递保障，测试环境 H2 适配）
CREATE TABLE domain_outbox (
    id             VARCHAR(36)   PRIMARY KEY,
    aggregate_id   VARCHAR(64)   NOT NULL,
    event_type     VARCHAR(64)   NOT NULL,
    payload_json   CLOB          NOT NULL,
    occurred_at    TIMESTAMP     NOT NULL,
    processed_at   TIMESTAMP     NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP     NOT NULL
);
CREATE INDEX idx_outbox_pending ON domain_outbox (processed_at, next_retry_at);
