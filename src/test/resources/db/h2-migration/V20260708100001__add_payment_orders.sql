-- H2 variant of V20260708100001__add_payment_orders.sql (JSON→CLOB, DATETIME→TIMESTAMP)
CREATE TABLE payment_orders (
    id            VARCHAR(36)   PRIMARY KEY,
    user_id       VARCHAR(64)   NOT NULL,
    plan_code     VARCHAR(32)   NOT NULL,
    amount        BIGINT        NOT NULL,
    currency      CHAR(3)       NOT NULL DEFAULT 'CNY',
    channel       VARCHAR(16)   NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    pay_url       VARCHAR(512),
    raw_response  CLOB,
    created_at    TIMESTAMP     NOT NULL,
    paid_at       TIMESTAMP     NULL
);
CREATE INDEX idx_payment_user ON payment_orders (user_id);
CREATE INDEX idx_payment_status ON payment_orders (status);
