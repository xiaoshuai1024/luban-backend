-- H2 variant of V20260621000002__add_page_versions.sql (JSON→CLOB, DATETIME→TIMESTAMP).
CREATE TABLE page_versions (
    id          VARCHAR(36)  PRIMARY KEY,
    page_id     VARCHAR(36)  NOT NULL,
    version_no  INT          NOT NULL,
    schema_json CLOB         NOT NULL,
    summary     VARCHAR(255),
    created_by  VARCHAR(36),
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_versions_page FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE,
    CONSTRAINT uk_page_version UNIQUE (page_id, version_no)
);
CREATE INDEX idx_versions_page_created ON page_versions (page_id, created_at);
