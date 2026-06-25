-- V2-T10 分析埋点：sites.analytics_json（GA4 / 百度统计 / Facebook Pixel 等配置）
-- 结构：{ "ga4": { "measurementId": "G-XXXX" }, "baidu": { "id": "xxxxx" }, "facebook": { "pixelId": "xxxxx" } }
ALTER TABLE sites ADD COLUMN analytics_json JSON NULL AFTER seo_json;
