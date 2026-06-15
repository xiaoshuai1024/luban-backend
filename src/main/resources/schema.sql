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

-- 留资表单（P0 留资闭环）
CREATE TABLE IF NOT EXISTS forms (
    id                  VARCHAR(36)  PRIMARY KEY,
    site_id             VARCHAR(36)  NOT NULL,
    page_id             VARCHAR(36)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    field_schema_json   JSON         NOT NULL,
    submit_config_json  JSON         NOT NULL,
    dedup_keys_json     JSON,
    dedup_window        INT          NOT NULL DEFAULT 86400,
    dedup_policy        VARCHAR(16)  NOT NULL DEFAULT 'reject',
    anti_spam_json      JSON,
    status              VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at          DATETIME(3)  NOT NULL,
    updated_at          DATETIME(3)  NOT NULL,
    CONSTRAINT fk_forms_site FOREIGN KEY (site_id) REFERENCES sites(id),
    CONSTRAINT fk_forms_page FOREIGN KEY (page_id) REFERENCES pages(id),
    KEY idx_forms_page (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 销售线索（contact_json 为 AES 加密后的联系人 JSON）
CREATE TABLE IF NOT EXISTS leads (
    id            VARCHAR(36)  PRIMARY KEY,
    site_id       VARCHAR(36)  NOT NULL,
    form_id       VARCHAR(36)  NOT NULL,
    page_id       VARCHAR(36)  NOT NULL,
    channel_id    VARCHAR(36),
    contact_json  JSON         NOT NULL,
    utm_json      JSON,
    status        VARCHAR(16)  NOT NULL DEFAULT 'new',
    assignee_id   VARCHAR(36),
    dedup_hash    VARCHAR(64)  NOT NULL,
    source_ip     VARCHAR(64),
    visitor_id    VARCHAR(64),
    converted_at  DATETIME(3),
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_form_dedup (form_id, dedup_hash),
    CONSTRAINT fk_leads_site FOREIGN KEY (site_id) REFERENCES sites(id),
    CONSTRAINT fk_leads_form FOREIGN KEY (form_id) REFERENCES forms(id),
    KEY idx_leads_site_status (site_id, status),
    KEY idx_leads_assignee (assignee_id, status),
    KEY idx_leads_created (site_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
