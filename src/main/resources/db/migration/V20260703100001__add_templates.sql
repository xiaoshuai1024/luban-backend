-- 模板市场（Template Marketplace）— 官方起步预留 UGC
-- 设计文档：packages/docs/luban-architecture-design（11-lowcode-engine-impl + 07-data-model）
-- 任务图：template-marketplace（DDD 聚合根 TemplateAggregate）
--
-- 三张表：
--   templates              模板目录条目（聚合根，状态机 draft→published→archived/featured）
--   template_versions      模板版本快照（schema_json，类比 page_versions）
--   template_installations 安装记录（审计 + 计数，谁装了哪个模板到哪个 page）
--
-- 约定对齐：UUID 主键 / DATETIME(3) / JSON / utf8mb4 / 无软删除（物理删除）

CREATE TABLE IF NOT EXISTS templates (
    id             VARCHAR(36) PRIMARY KEY,
    slug           VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    category       VARCHAR(32) NOT NULL,
    description    VARCHAR(512),
    thumbnail      VARCHAR(255),
    author_id      VARCHAR(36),
    status         VARCHAR(16) NOT NULL DEFAULT 'draft',
    latest_version INT NOT NULL DEFAULT 1,
    created_at     DATETIME(3) NOT NULL,
    updated_at     DATETIME(3) NOT NULL,
    UNIQUE KEY uk_templates_slug (slug),
    KEY idx_templates_category (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS template_versions (
    id           VARCHAR(36) PRIMARY KEY,
    template_id  VARCHAR(36) NOT NULL,
    version      INT NOT NULL,
    schema_json  JSON NOT NULL,
    change_note  VARCHAR(255),
    created_at   DATETIME(3) NOT NULL,
    UNIQUE KEY uk_tpl_version (template_id, version),
    CONSTRAINT fk_tplv_tpl FOREIGN KEY (template_id) REFERENCES templates(id) ON DELETE CASCADE,
    KEY idx_tplv_tpl (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS template_installations (
    id           VARCHAR(36) PRIMARY KEY,
    template_id  VARCHAR(36) NOT NULL,
    version      INT NOT NULL,
    site_id      VARCHAR(36) NOT NULL,
    page_id      VARCHAR(36) NOT NULL,
    installer_id VARCHAR(36),
    created_at   DATETIME(3) NOT NULL,
    CONSTRAINT fk_inst_tpl FOREIGN KEY (template_id) REFERENCES templates(id),
    CONSTRAINT fk_inst_site FOREIGN KEY (site_id) REFERENCES sites(id),
    KEY idx_inst_tpl (template_id),
    KEY idx_inst_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
