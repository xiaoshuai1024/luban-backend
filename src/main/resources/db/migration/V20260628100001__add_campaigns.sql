-- V20260628100001__add_campaigns.sql
-- 营销活动表（app-deeplink-backend-arch plan T11/T7）
-- 对齐 packages/docs/luban-architecture-design/docs/07-data-model.md:106-121
-- ID 适配为 VARCHAR(36) UUID（非设计文档的 BIGINT Snowflake，对齐现状 Site.id 模式）
-- channel.campaign_id 引用本表，故本迁移先于 channels(V20260628100002) 执行

CREATE TABLE IF NOT EXISTS campaigns (
    id          VARCHAR(36)   PRIMARY KEY,
    site_id     VARCHAR(36)   NOT NULL,
    name        VARCHAR(128)  NOT NULL,
    start_at    DATETIME(3),
    end_at      DATETIME(3),
    status      VARCHAR(16)   NOT NULL DEFAULT 'planned',
    created_at  DATETIME(3)   NOT NULL,
    updated_at  DATETIME(3)   NOT NULL,
    CONSTRAINT fk_campaigns_site FOREIGN KEY (site_id) REFERENCES sites(id),
    KEY idx_campaigns_site (site_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
