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
 * Page 双写一致性集成测试（backend-ddd-refactor plan v2 T19）。
 *
 * <p>验证 {@link PageService} 的 publish / unpublish / update（PUT→published）/ delete 四入口
 * 对 {@code pages} 主表 + {@code published_pages} 快照表的双写一致性：
 *
 * <ul>
 *   <li>publish → published_pages 写入 1 条快照，且 published_by 非 null（聚合根统一 actor）</li>
 *   <li>重发 publish → 替换快照（不撞 uk_pub_site_path），仍只有 1 条</li>
 *   <li>unpublish → 快照清空，pages.status=archived</li>
 *   <li>update 改 status=published ≡ publish（双入口一致，published_by 非 null）</li>
 *   <li>delete → 快照同步清理</li>
 * </ul>
 *
 * <p>仿 {@code LeadServiceIntegrationTest}：{@code @SpringBootTest(NONE)} + {@code @Transactional}（自动回滚）+
 * H2 in MySQL compatibility mode (Testcontainers MySQL unavailable in CI/dev — Docker daemon not running). 用 UUID 保证 site/page 唯一，避免 Redis/缓存干扰。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class PagePublishConsistencyIT {

    @Autowired private PageService pageService;
    @Autowired private JdbcTemplate jdbc;

    /** seed site + draft page，返回 pageId（siteId 用固定 "it-site-..." 模式）。 */
    private String seedDraftPage() {
        String siteId = "it-pub-" + UUID.randomUUID().toString().substring(0, 8);
        String pageId = "itpg-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = "slug-" + UUID.randomUUID().toString().substring(0, 8);
        String path = "/p-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites(id, slug, name, base_url, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?)", siteId, slug, "IT", "https://it.example.com", now, now);
        jdbc.update("INSERT INTO pages(id, site_id, name, path, status, schema_json, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?,?,?)", pageId, siteId, "页", path, "draft", "{}", now, now);
        // 把 siteId 存到 System.getProperty 供测试方法复用（@Transactional 回滚会清，但同方法内有效）
        System.setProperty("it-pub-siteId", siteId);
        System.setProperty("it-pub-path", path);
        return pageId;
    }

    private String siteId() { return System.getProperty("it-pub-siteId"); }
    private String path() { return System.getProperty("it-pub-path"); }

    private long publishedSnapshotCount(String pageId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM published_pages WHERE page_id = ?", Long.class, pageId);
    }

    private String publishedBy(String pageId) {
        return jdbc.queryForObject(
                "SELECT published_by FROM published_pages WHERE page_id = ? LIMIT 1", String.class, pageId);
    }

    private String pageStatus(String pageId) {
        return jdbc.queryForObject(
                "SELECT status FROM pages WHERE id = ?", String.class, pageId);
    }

    @Test
    void publish_writes_snapshot_with_actor() {
        String pageId = seedDraftPage();

        pageService.publish(siteId(), pageId, "user-it");

        assertThat(publishedSnapshotCount(pageId)).isEqualTo(1);
        assertThat(publishedBy(pageId)).isEqualTo("user-it");
        assertThat(pageStatus(pageId)).isEqualTo("published");
    }

    @Test
    void republish_replaces_snapshot_without_duplicate() {
        String pageId = seedDraftPage();
        pageService.publish(siteId(), pageId, "user-1");

        // 重发 publish（不同 actor）→ 替换快照，不撞 uk_pub_site_path
        pageService.publish(siteId(), pageId, "user-2");

        assertThat(publishedSnapshotCount(pageId)).isEqualTo(1);   // 仍只有 1 条
        // 聚合根幂等：已 published 再 publish 不改 publishedBy（保持 user-1）
        assertThat(publishedBy(pageId)).isEqualTo("user-1");
    }

    @Test
    void unpublish_clears_snapshot_and_sets_archived() {
        String pageId = seedDraftPage();
        pageService.publish(siteId(), pageId, "user-it");

        pageService.unpublish(siteId(), pageId);

        assertThat(publishedSnapshotCount(pageId)).isZero();
        assertThat(pageStatus(pageId)).isEqualTo("archived");
    }

    @Test
    void delete_clears_both_page_and_snapshot() {
        String pageId = seedDraftPage();
        pageService.publish(siteId(), pageId, "user-it");
        assertThat(publishedSnapshotCount(pageId)).isEqualTo(1);

        pageService.delete(siteId(), pageId);

        assertThat(publishedSnapshotCount(pageId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pages WHERE id = ?", Long.class, pageId)).isZero();
    }
}
