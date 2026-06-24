-- H2 (MySQL compatibility mode) variant of V20260619000001__add_datasources.sql.
-- See V20260614000000__init_schema.sql header for rationale: JSON → CLOB (H2 JSON
-- double-encodes), ENGINE/CHARSET dropped, DATETIME(3) → TIMESTAMP. Column names,
-- UNIQUE KEY and FOREIGN KEY constraints are identical to production so contract
-- tests exercise the real schema semantics.

CREATE TABLE datasources (
    id          VARCHAR(36)  PRIMARY KEY,
    site_id     VARCHAR(36)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    config_json CLOB         NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_datasources_site_name UNIQUE (site_id, name),
    CONSTRAINT fk_datasources_site FOREIGN KEY (site_id) REFERENCES sites(id)
);
