-- H2 variant of V20260621000001__add_collections.sql (JSON→CLOB, DATETIME→TIMESTAMP).
CREATE TABLE collections (
    id                VARCHAR(36)  PRIMARY KEY,
    site_id           VARCHAR(36)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    field_schema_json CLOB         NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT uk_collections_site_name UNIQUE (site_id, name),
    CONSTRAINT fk_collections_site FOREIGN KEY (site_id) REFERENCES sites(id)
);

CREATE TABLE collection_items (
    id             VARCHAR(36)  PRIMARY KEY,
    collection_id  VARCHAR(36)  NOT NULL,
    data_json      CLOB         NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    CONSTRAINT fk_items_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE
);
CREATE INDEX idx_items_collection_status ON collection_items (collection_id, status);
CREATE INDEX idx_items_updated ON collection_items (collection_id, updated_at);
