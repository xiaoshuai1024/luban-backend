-- H2 variant of V20260628100001__add_campaigns.sql (JSON→CLOB, DATETIME→TIMESTAMP).
-- app-deeplink-backend-arch plan T11/T7（测试环境 H2 适配）
CREATE TABLE campaigns (
    id          VARCHAR(36)   PRIMARY KEY,
    site_id     VARCHAR(36)   NOT NULL,
    name        VARCHAR(128)  NOT NULL,
    start_at    TIMESTAMP,
    end_at      TIMESTAMP,
    status      VARCHAR(16)   NOT NULL DEFAULT 'planned',
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT fk_campaigns_site FOREIGN KEY (site_id) REFERENCES sites(id)
);
CREATE INDEX idx_campaigns_site ON campaigns (site_id, status);
