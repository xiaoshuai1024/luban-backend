-- 支付订单表（P-001 计费/订阅闭环，billing-payment-ui plan T1）
--
-- 记录用户升级套餐时的支付订单。内测期 amount=0（直通 PAID），
-- 未来接真实微信/支付宝时记录支付链接/平台响应。
--
-- 状态机：PENDING → PAID（amount==0 直通 / 回调）/ FAILED（回调失败）/ CANCELLED

CREATE TABLE IF NOT EXISTS payment_orders (
    id            VARCHAR(36)   PRIMARY KEY,
    user_id       VARCHAR(64)   NOT NULL,
    plan_code     VARCHAR(32)   NOT NULL,
    amount        BIGINT        NOT NULL,           -- 分（避免浮点）
    currency      CHAR(3)       NOT NULL DEFAULT 'CNY',
    channel       VARCHAR(16)   NOT NULL,           -- WECHAT / ALIPAY
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING/PAID/FAILED/CANCELLED
    pay_url       VARCHAR(512),                     -- 支付链接/二维码 URL
    raw_response  JSON,                             -- 支付平台原始响应（未来用）
    created_at    DATETIME(3)   NOT NULL,
    paid_at       DATETIME(3)   NULL,
    KEY idx_payment_user (user_id),
    KEY idx_payment_status (status)
);
