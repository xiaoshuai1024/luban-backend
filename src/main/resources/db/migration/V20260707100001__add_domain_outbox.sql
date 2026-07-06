-- 领域事件 outbox（at-least-once 投递保障，backend-ddd-cleanup plan 阶段 3）
--
-- 设计：Service 在聚合根 pullEvents() 后，同事务内双写——
--   ① ApplicationEventPublisher.publishEvent（触发 AFTER_COMMIT handler，即时 best-effort）
--   ② INSERT domain_outbox（事务内落盘，保证不丢）
-- OutboxRelayScheduler 定时扫未处理记录补偿重发，实现 at-least-once。
--
-- 字段约定：UUID 主键 / DATETIME(3) / JSON payload / utf8mb4
-- 幂等由 handler 端去重保证（relay 可能重投同一事件）。

CREATE TABLE IF NOT EXISTS domain_outbox (
    id             VARCHAR(36)   PRIMARY KEY,
    aggregate_id   VARCHAR(64)   NOT NULL,
    event_type     VARCHAR(64)   NOT NULL,
    payload_json   JSON          NOT NULL,
    occurred_at    DATETIME(3)   NOT NULL,
    processed_at   DATETIME(3)   NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    next_retry_at  DATETIME(3)   NOT NULL,
    KEY idx_outbox_pending (processed_at, next_retry_at)
);
