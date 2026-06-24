-- H2 variant of V20260621000003__add_seo_columns.sql (JSON→CLOB).
ALTER TABLE pages ADD COLUMN seo_json CLOB;
ALTER TABLE sites ADD COLUMN seo_json CLOB;
