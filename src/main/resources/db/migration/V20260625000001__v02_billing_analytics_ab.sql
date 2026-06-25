-- v02: 转化分析 + 商业化骨架（T-be-1）
-- 8 张表：billing 4 + analytics 2 + ab 3。不改现有表，幂等追加。
-- ======================================================================

-- ===== v02: billing 域（T-be-2~5 依赖）=====
-- 套餐定义：Free/Starter/Growth，价格全 0，含配额 + gates 放行集 + 试用期天数
CREATE TABLE IF NOT EXISTS plans (
    plan_code     VARCHAR(32) PRIMARY KEY,
    name          VARCHAR(64) NOT NULL,
    price_monthly BIGINT NOT NULL DEFAULT 0,
    quota_leads   INT NOT NULL DEFAULT 0,
    quota_pages   INT NOT NULL DEFAULT 0,
    quota_visits  INT NOT NULL DEFAULT 0,
    gates         JSON,
    trial_days    INT NOT NULL DEFAULT 0,
    sort_order    INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户当前订阅：status = active/trialing/expired；关联 plan_code
CREATE TABLE IF NOT EXISTS subscriptions (
    user_id          VARCHAR(64) PRIMARY KEY,
    plan_code        VARCHAR(32) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'active',
    started_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at       DATETIME(3) NULL,
    trial_started_at DATETIME(3) NULL,
    trial_ends_at    DATETIME(3) NULL,
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_code) REFERENCES plans(plan_code),
    KEY idx_sub_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 试用记录：trial_plan_code + 时间窗口 + converted_to（降级去向）
CREATE TABLE IF NOT EXISTS trial_records (
    user_id          VARCHAR(64) PRIMARY KEY,
    trial_plan_code  VARCHAR(32) NOT NULL,
    started_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ends_at          DATETIME(3) NOT NULL,
    converted_to     VARCHAR(32) NULL,
    CONSTRAINT fk_trial_plan FOREIGN KEY (trial_plan_code) REFERENCES plans(plan_code),
    KEY idx_trial_ends (ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 月度用量计数：user_id + period_month(YYYY-MM) + metric 唯一，原子累加
-- 并发安全：INSERT ... ON DUPLICATE KEY UPDATE count = count + 1
CREATE TABLE IF NOT EXISTS usage_counters (
    id           VARCHAR(64) PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    period_month CHAR(7) NOT NULL,
    metric       VARCHAR(32) NOT NULL,
    count        BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_usage (user_id, period_month, metric),
    KEY idx_usage_user (user_id, period_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== v02: analytics 域（T-be-6~8 依赖）=====
-- 原始事件：免鉴权接收 + AES 脱敏 source_ip → source_ip_hashed
CREATE TABLE IF NOT EXISTS analytics_events (
    id                VARCHAR(64) PRIMARY KEY,
    site_id           VARCHAR(64) NOT NULL,
    visitor_id        VARCHAR(64) NOT NULL,
    session_id        VARCHAR(64),
    event_type        VARCHAR(32) NOT NULL,
    event_payload     JSON,
    page_id           VARCHAR(64),
    variant_id        VARCHAR(64),
    utm_json          JSON,
    client_ts         DATETIME(3) NULL,
    server_ts         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    source_ip_hashed  VARCHAR(128),
    CONSTRAINT fk_ae_site FOREIGN KEY (site_id) REFERENCES sites(id),
    KEY idx_ae_site_ts (site_id, server_ts),
    KEY idx_ae_event (site_id, event_type, server_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预聚合日表：site_id + date + page_id + variant_id 唯一
CREATE TABLE IF NOT EXISTS analytics_daily (
    id          VARCHAR(64) PRIMARY KEY,
    site_id     VARCHAR(64) NOT NULL,
    date        DATE NOT NULL,
    page_id     VARCHAR(64),
    variant_id  VARCHAR(64),
    views       BIGINT NOT NULL DEFAULT 0,
    submissions BIGINT NOT NULL DEFAULT 0,
    conversions BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ad (site_id, date, page_id, variant_id),
    KEY idx_ad_site_date (site_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== v02: ab 域（T-be-9~11 依赖）=====
-- 实验：单页单 running 约束（应用层校验）；status = draft/running/paused/ended
CREATE TABLE IF NOT EXISTS ab_experiments (
    id         VARCHAR(64) PRIMARY KEY,
    site_id    VARCHAR(64) NOT NULL,
    page_id    VARCHAR(64) NOT NULL,
    name       VARCHAR(128) NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'draft',
    traffic_pct INT NOT NULL DEFAULT 100,
    start_at   DATETIME(3) NULL,
    end_at     DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ab_site FOREIGN KEY (site_id) REFERENCES sites(id),
    KEY idx_ab_site (site_id, status),
    KEY idx_ab_page (page_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 变体：experiment_id + label(A/B) 唯一；page_version_id 指向 page_versions
CREATE TABLE IF NOT EXISTS ab_variants (
    id              VARCHAR(64) PRIMARY KEY,
    experiment_id   VARCHAR(64) NOT NULL,
    label           VARCHAR(8) NOT NULL,
    page_version_id VARCHAR(64) NOT NULL,
    weight          INT NOT NULL DEFAULT 50,
    is_control      TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_abv_exp FOREIGN KEY (experiment_id) REFERENCES ab_experiments(id) ON DELETE CASCADE,
    UNIQUE KEY uk_abv (experiment_id, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分桶：visitor_id + experiment_id 唯一 → variant_id（一致性哈希稳定分桶）
CREATE TABLE IF NOT EXISTS ab_assignments (
    id           VARCHAR(64) PRIMARY KEY,
    visitor_id   VARCHAR(64) NOT NULL,
    experiment_id VARCHAR(64) NOT NULL,
    variant_id   VARCHAR(64) NOT NULL,
    assigned_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_aba (visitor_id, experiment_id),
    KEY idx_aba_exp (experiment_id, variant_id),
    CONSTRAINT fk_aba_exp FOREIGN KEY (experiment_id) REFERENCES ab_experiments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- v02 seed: 三档套餐（价格全 0，逻辑完整）
INSERT IGNORE INTO plans (plan_code, name, price_monthly, quota_leads, quota_pages, quota_visits, gates, trial_days, sort_order) VALUES
    ('free',    '免费版', 0, 100,  3, 1000,  JSON_ARRAY('lead_capture'), 0,  1),
    ('starter', '入门版', 0, 1000, 10, 10000, JSON_ARRAY('lead_capture', 'realtime_collab', 'page_versioning'), 14, 2),
    ('growth',  '成长版', 0, 5000, 50, 50000, JSON_ARRAY('lead_capture', 'realtime_collab', 'page_versioning', 'poster_export', 'analytics', 'ab_testing'), 14, 3);
