-- H2 variant of V20260621000004__add_site_analytics.sql (JSON→CLOB).
ALTER TABLE sites ADD COLUMN analytics_json CLOB;
