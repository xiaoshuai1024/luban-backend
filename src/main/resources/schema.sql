-- Idempotent schema (aligned with luban-backend-go dao/mysql.go)
CREATE TABLE IF NOT EXISTS sites (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(128) NOT NULL,
    base_url   VARCHAR(512),
    status     VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_sites_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pages (
    id          VARCHAR(36)  PRIMARY KEY,
    site_id     VARCHAR(36)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    path        VARCHAR(255) NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'draft',
    schema_json JSON         NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_site_path (site_id, path),
    CONSTRAINT fk_pages_site FOREIGN KEY (site_id) REFERENCES sites(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id         VARCHAR(36)  PRIMARY KEY,
    username   VARCHAR(128) NOT NULL,
    name       VARCHAR(255),
    role       VARCHAR(32)  NOT NULL DEFAULT 'user',
    status     VARCHAR(32)  NOT NULL DEFAULT 'active',
    password   VARCHAR(255) NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_settings (
    id         TINYINT       PRIMARY KEY,
    data_json  JSON         NOT NULL,
    updated_at DATETIME(3)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
