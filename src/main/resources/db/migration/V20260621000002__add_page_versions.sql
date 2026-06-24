-- V2-T8 版本历史：page_versions 快照表
-- 每次保存页面生成一条快照；回滚 = 读快照 schema 覆盖 page.schema_json + 新建一条版本（复制语义）
-- 保留策略：每页最近 50 版，应用层 deleteOlderThan 清理
CREATE TABLE page_versions (
  id VARCHAR(36) PRIMARY KEY,
  page_id VARCHAR(36) NOT NULL,
  version_no INT NOT NULL,
  schema_json JSON NOT NULL,
  summary VARCHAR(255),
  created_by VARCHAR(36),
  created_at DATETIME(3) NOT NULL,
  CONSTRAINT fk_versions_page FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE,
  UNIQUE KEY uk_page_version (page_id, version_no),
  KEY idx_versions_page_created (page_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
