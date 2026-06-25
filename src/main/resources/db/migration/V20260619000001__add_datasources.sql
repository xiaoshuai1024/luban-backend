-- Datasources: per-site data source definitions (static blob or external API).
-- Aligned with luban-backend-go dao/mysql.go initSchema (§9.3 DDL, identical columns/constraints).
-- type whitelist 'static' | 'api' enforced in service layer (Java DatasourceService / Go DatasourceService).

CREATE TABLE datasources (
    id          VARCHAR(36)  PRIMARY KEY,
    site_id     VARCHAR(36)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    config_json JSON         NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_datasources_site_name (site_id, name),
    CONSTRAINT fk_datasources_site FOREIGN KEY (site_id) REFERENCES sites(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
