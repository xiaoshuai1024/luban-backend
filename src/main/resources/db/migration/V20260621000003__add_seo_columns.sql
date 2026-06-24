-- V2-T2 SEO 元信息：pages + sites 增加 seo_json JSON 列
-- 持久化 PageSchema.seo 结构（title/description/keywords/og*/canonical/noIndex）。
-- 公开端点 PublicPageService 透出 seo 供 website useSeoMeta 注入。

ALTER TABLE pages ADD COLUMN seo_json JSON NULL AFTER schema_json;
ALTER TABLE sites ADD COLUMN seo_json JSON NULL AFTER base_url;
