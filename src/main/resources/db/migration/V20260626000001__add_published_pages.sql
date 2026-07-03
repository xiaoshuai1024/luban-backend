-- V20260626000001__add_published_pages.sql
-- P0 发布闭环：草稿/发布数据分离
-- 1. 新增 published_pages 表（存储发布快照，与 pages 草稿表分离）
-- 2. pages 表加发布审计字段
-- 3. 回填：已 published 的页面拷贝到 published_pages

-- 1. 新增 published_pages 表
CREATE TABLE published_pages (
    id           VARCHAR(36) PRIMARY KEY,
    page_id      VARCHAR(36) NOT NULL,
    site_id      VARCHAR(36) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    path         VARCHAR(512) NOT NULL,
    schema_json  JSON NOT NULL,
    seo_json     JSON,
    published_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_by VARCHAR(36),
    UNIQUE KEY uk_pub_site_path (site_id, path),
    KEY idx_pub_page_id (page_id),
    CONSTRAINT fk_pub_page FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. pages 表加发布审计字段
ALTER TABLE pages ADD COLUMN published_at TIMESTAMP NULL;
ALTER TABLE pages ADD COLUMN published_by VARCHAR(36) NULL;

-- 3. 回填：已 published 的页面拷贝到 published_pages
INSERT INTO published_pages (id, page_id, site_id, name, path, schema_json, seo_json, published_at)
SELECT REPLACE(UUID(), '-', ''), id, site_id, name, path, schema_json, seo_json, COALESCE(published_at, updated_at)
FROM pages WHERE status = 'published';
