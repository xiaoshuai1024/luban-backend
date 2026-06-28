-- H2 variant of V20260628100002__add_channels.sql (JSON→CLOB, DATETIME→TIMESTAMP).
-- app-deeplink-backend-arch plan T10/T7（测试环境 H2 适配）
CREATE TABLE channels (
    id              VARCHAR(36)   PRIMARY KEY,
    site_id         VARCHAR(36)   NOT NULL,
    campaign_id     VARCHAR(36),
    code            VARCHAR(32)   NOT NULL,
    type            VARCHAR(16)   NOT NULL,
    utm_template    CLOB,
    short_url       VARCHAR(64)   NOT NULL,
    target_page_id  VARCHAR(36)   NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    CONSTRAINT uk_short_url UNIQUE (short_url),
    CONSTRAINT uk_site_code UNIQUE (site_id, code),
    CONSTRAINT fk_channels_site FOREIGN KEY (site_id) REFERENCES sites(id),
    CONSTRAINT fk_channels_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id),
    CONSTRAINT fk_channels_page FOREIGN KEY (target_page_id) REFERENCES pages(id)
);
CREATE INDEX idx_channels_campaign ON channels (campaign_id);
CREATE INDEX idx_channels_page ON channels (target_page_id);
