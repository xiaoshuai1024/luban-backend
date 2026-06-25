-- H2 (MySQL compatibility mode) variant of the production Flyway baseline.
--
-- Differences from the production V20260614000000__init_schema.sql:
--   - JSON columns → CLOB: H2's JSON type JSON-encodes plain-string parameters on
--     insert, which would break PageResponse's readTree(schemaJson) parsing and
--     cause the schema field to serialize as a quoted string instead of a nested
--     object. CLOB keeps schema_json as the raw text the application wrote, so
--     MyBatis reads back the same String value MySQL would return. This preserves
--     the production contract (schemaJson is a JSON document text).
--   - ENGINE=InnoDB / DEFAULT CHARSET dropped: H2 ignores them but they can warn.
--
-- Column types, names, constraints (UNIQUE KEY, FOREIGN KEY) are identical to
-- production so contract tests exercise the real schema semantics.

CREATE TABLE sites (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(128) NOT NULL,
    base_url   VARCHAR(512),
    status     VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sites_slug UNIQUE (slug)
);

CREATE TABLE pages (
    id          VARCHAR(36)  PRIMARY KEY,
    site_id     VARCHAR(36)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    path        VARCHAR(255) NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'draft',
    schema_json CLOB         NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_site_path UNIQUE (site_id, path),
    CONSTRAINT fk_pages_site FOREIGN KEY (site_id) REFERENCES sites(id)
);

CREATE TABLE users (
    id         VARCHAR(36)  PRIMARY KEY,
    username   VARCHAR(128) NOT NULL,
    name       VARCHAR(255),
    role       VARCHAR(32)  NOT NULL DEFAULT 'user',
    status     VARCHAR(32)  NOT NULL DEFAULT 'active',
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE system_settings (
    id         TINYINT      PRIMARY KEY,
    data_json  CLOB         NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_pages_site_path_status ON pages(site_id, path, status);
