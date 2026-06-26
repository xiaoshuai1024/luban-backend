-- H2 兼容版：published_pages 表（P0 发布闭环）
-- 注意：H2 不支持 ENGINE/CHARSET 语法，FK 语法略有不同

CREATE TABLE IF NOT EXISTS published_pages (
    id           VARCHAR(36) PRIMARY KEY,
    page_id      VARCHAR(36) NOT NULL,
    site_id      VARCHAR(36) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    path         VARCHAR(512) NOT NULL,
    schema_json  CLOB NOT NULL,
    seo_json     CLOB,
    published_at TIMESTAMP,
    published_by VARCHAR(36)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pub_site_path ON published_pages (site_id, path);
CREATE INDEX IF NOT EXISTS idx_pub_page_id ON published_pages (page_id);

-- pages 表加发布审计字段（H2 ALTER）
ALTER TABLE pages ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;
ALTER TABLE pages ADD COLUMN IF NOT EXISTS published_by VARCHAR(36);
