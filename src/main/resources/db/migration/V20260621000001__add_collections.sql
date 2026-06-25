-- V2-T7 CMS 内容集合：collections + collection_items 两表
-- 对齐 Go dao/mysql.go initSchema（表名/约束名严格一致）。
-- field_schema_json / data_json 用 JSON 类型；Go 端用 json.RawMessage（禁 []byte）。

CREATE TABLE collections (
  id VARCHAR(36) PRIMARY KEY,
  site_id VARCHAR(36) NOT NULL,
  name VARCHAR(255) NOT NULL,
  field_schema_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_collections_site_name (site_id, name),
  CONSTRAINT fk_collections_site FOREIGN KEY (site_id) REFERENCES sites(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE collection_items (
  id VARCHAR(36) PRIMARY KEY,
  collection_id VARCHAR(36) NOT NULL,
  data_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  CONSTRAINT fk_items_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
  KEY idx_items_collection_status (collection_id, status),
  KEY idx_items_updated (collection_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
