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
    contact_json  TEXT         NOT NULL,
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

-- 页面版本快照（发布即建版本，plan §3.4）
CREATE TABLE IF NOT EXISTS page_versions (
    id          VARCHAR(36) PRIMARY KEY,
    site_id     VARCHAR(36) NOT NULL,
    page_id     VARCHAR(36) NOT NULL,
    version     INT NOT NULL,
    schema_json JSON NOT NULL,
    operator_id VARCHAR(36),
    created_at  DATETIME(3) NOT NULL,
    UNIQUE KEY uk_page_version (page_id, version),
    KEY idx_pv_page (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 特性开关（site 维度，plan §3.5）
CREATE TABLE IF NOT EXISTS feature_gates (
    site_id  VARCHAR(36) NOT NULL,
    gate_key VARCHAR(64) NOT NULL,
    enabled  TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (site_id, gate_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 线索审计日志（解密查看 / 状态转移，plan §3.1 审计口径）
CREATE TABLE IF NOT EXISTS lead_audit_logs (
    id         VARCHAR(36) PRIMARY KEY,
    site_id    VARCHAR(36) NOT NULL,
    lead_id    VARCHAR(36) NOT NULL,
    actor_id   VARCHAR(36) NOT NULL,
    action     VARCHAR(32) NOT NULL,
    detail     JSON,
    created_at DATETIME(3) NOT NULL,
    KEY idx_audit_lead (site_id, lead_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-站点授权映射（🟡 tenant authz：非 admin 用户仅能访问已授权站点）
CREATE TABLE IF NOT EXISTS user_sites (
    user_id  VARCHAR(36) NOT NULL,
    site_id  VARCHAR(36) NOT NULL,
    role     VARCHAR(16) NOT NULL DEFAULT 'member',
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, site_id),
    KEY idx_us_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
