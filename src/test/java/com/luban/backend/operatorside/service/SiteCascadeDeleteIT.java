package com.luban.backend.operatorside.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Site 级联删除集成测试（backend-ddd-refactor plan v2 T19）。
 *
 * <p>验证 {@link SiteService#delete} 的 7 表级联删除 + page_versions FK CASCADE +
 * @Transactional 原子性。仿 {@code LeadServiceIntegrationTest} 范式：
 * {@code @SpringBootTest(NONE)} + {@code @Transactional}（自动回滚）+ H2 in MySQL compatibility mode (Testcontainers MySQL unavailable in CI/dev — Docker daemon not running).
 *
 * <p>对齐 app-deeplink-backend-arch T8：级联含 channels/campaigns（短链子表）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class SiteCascadeDeleteIT {

    @Autowired private SiteService siteService;
    @Autowired private JdbcTemplate jdbc;

    /** seed 一个 site（含唯一 slug）+ 指定子表的 1 条关联行。返回 siteId。 */
    private String seedSiteWithChildren(boolean leads, boolean forms, boolean datasources,
                                        boolean collections, boolean channels, boolean campaigns,
                                        boolean pages) {
        String siteId = "it-site-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = "slug-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        // sites: id(主键), slug, name, status(默认 active), base_url, created_at, updated_at
        jdbc.update("INSERT INTO sites(id, slug, name, base_url, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?)", siteId, slug, "IT 站点", "https://it.example.com", now, now);

        // pages 必须先于 channels/forms（channels.target_page_id NOT NULL，forms.page_id 引用）
        if (pages) {
            jdbc.update("INSERT INTO pages(id, site_id, name, path, schema_json, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?,?)", "pg-" + siteId, siteId, "首页", "/home-" + siteId, "{}", now, now);
        }
        if (campaigns) {
            jdbc.update("INSERT INTO campaigns(id, site_id, name, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?)", "camp-" + siteId, siteId, "活动", now, now);
        }
        if (channels) {
            // short_url NOT NULL UNIQUE + target_page_id NOT NULL（需 page 已存在）
            jdbc.update("INSERT INTO channels(id, site_id, campaign_id, code, type, short_url, target_page_id, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)", "ch-" + siteId, siteId, "camp-" + siteId, "c" + siteId,
                    "qrcode", "su-" + siteId, "pg-" + siteId, now, now);
        }
        if (collections) {
            // field_schema_json NOT NULL，必须提供（{} 表示无字段定义）
            jdbc.update("INSERT INTO collections(id, site_id, name, field_schema_json, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?)", "col-" + siteId, siteId, "集合", "{}", now, now);
        }
        if (datasources) {
            jdbc.update("INSERT INTO datasources(id, site_id, name, type, config_json, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?,?)", "ds-" + siteId, siteId, "数据源", "static", "{}", now, now);
        }
        if (forms) {
            // forms: field_schema_json + submit_config_json NOT NULL
            jdbc.update("INSERT INTO forms(id, site_id, page_id, name, field_schema_json, submit_config_json, dedup_window, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)", "fm-" + siteId, siteId, "pg-" + siteId, "表单", "[]", "{}", 3600, now, now);
        }
        if (leads) {
            // leads: page_id NOT NULL
            jdbc.update("INSERT INTO leads(id, site_id, form_id, page_id, contact_json, status, dedup_hash, created_at, updated_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)", "ld-" + siteId, siteId, "fm-" + siteId, "pg-" + siteId, "{}", "new", "hash" + siteId, now, now);
        }
        return siteId;
    }

    /** 子表用 site_id 查；sites 表主键是 id（无 site_id 列）。 */
    private long countRows(String table, String siteId) {
        String column = "sites".equals(table) ? "id" : "site_id";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, siteId);
    }

    @Test
    void delete_cascades_to_all_seven_child_tables() {
        String siteId = seedSiteWithChildren(true, true, true, true, true, true, true);

        siteService.delete(siteId);

        // 7 子表行全消失 + sites 表行消失
        assertThat(countRows("leads", siteId)).isZero();
        assertThat(countRows("forms", siteId)).isZero();
        assertThat(countRows("datasources", siteId)).isZero();
        assertThat(countRows("collections", siteId)).isZero();
        assertThat(countRows("channels", siteId)).isZero();
        assertThat(countRows("campaigns", siteId)).isZero();
        assertThat(countRows("pages", siteId)).isZero();
        assertThat(countRows("sites", siteId)).isZero();
    }

    @Test
    void delete_cleans_page_versions_via_fk_cascade() {
        String siteId = seedSiteWithChildren(false, false, false, false, false, false, true);
        String pageId = "pg-" + siteId;
        Instant now = Instant.now();
        // seed page_versions（FK→pages ON DELETE CASCADE）；列名是 version_no
        jdbc.update("INSERT INTO page_versions(id, page_id, version_no, schema_json, created_by, created_at) " +
                "VALUES(?,?,?,?,?,?)", "pv-" + siteId, pageId, 1, "{}", "tester", now);

        siteService.delete(siteId);

        // pages 删除后 page_versions 经 FK CASCADE 自动清
        assertThat(countRows("pages", siteId)).isZero();
        Integer pvCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM page_versions WHERE page_id = ?", Integer.class, pageId);
        assertThat(pvCount).isZero();
    }

    @Test
    void delete_with_minimal_site_no_children_succeeds() {
        // 无子表数据的 site 删除也应成功
        String siteId = seedSiteWithChildren(false, false, false, false, false, false, false);

        siteService.delete(siteId);

        assertThat(countRows("sites", siteId)).isZero();
    }
}
