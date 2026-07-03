-- V20260628100002__add_channels.sql
-- 渠道/短链表（app-deeplink-backend-arch plan T10/T7）
-- 对齐 packages/docs/luban-architecture-design/docs/07-data-model.md:124-143
-- ID 适配为 VARCHAR(36) UUID（非 BIGINT Snowflake，对齐现状）
-- campaign_id 可空（plan 决策 4：channel 可独立存在不挂活动）

CREATE TABLE IF NOT EXISTS channels (
    id              VARCHAR(36)   PRIMARY KEY,
    site_id         VARCHAR(36)   NOT NULL,
    campaign_id     VARCHAR(36),
    code            VARCHAR(32)   NOT NULL,
    type            VARCHAR(16)   NOT NULL,
    utm_template    JSON,
    short_url       VARCHAR(64)   NOT NULL,
    target_page_id  VARCHAR(36)   NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'active',
    created_at      DATETIME(3)   NOT NULL,
    updated_at      DATETIME(3)   NOT NULL,
    UNIQUE KEY uk_short_url (short_url),
    UNIQUE KEY uk_site_code (site_id, code),
    CONSTRAINT fk_channels_site FOREIGN KEY (site_id) REFERENCES sites(id),
    CONSTRAINT fk_channels_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id),
    CONSTRAINT fk_channels_page FOREIGN KEY (target_page_id) REFERENCES pages(id),
    KEY idx_channels_campaign (campaign_id),
    KEY idx_channels_page (target_page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
