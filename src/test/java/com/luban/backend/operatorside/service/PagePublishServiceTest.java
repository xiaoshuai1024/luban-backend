package com.luban.backend.operatorside.service;
import com.luban.backend.publicside.service.PublicPageService;
import com.luban.backend.operatorside.service.PageService;

import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 3: PageService 发布闭环单测（直接调 Service 层，绕过 MockMvc context-path 问题）。
 *
 * 测试链路：publish → 公开可见 → 编辑草稿不影响线上 → unpublish → 不可见。
 */
@SpringBootTest
@ActiveProfiles("test")
class PagePublishServiceTest {

    @Autowired
    PageService pageService;
    @Autowired
    com.luban.backend.publicside.service.PublicPageService publicPageService;
    @Autowired
    JdbcTemplate jdbc;

    private static final String SITE_ID = "site-pub-svc";
    private static final String SLUG = "pub-svc-slug";
    private static final String PAGE_ID = "page-pub-svc-1";
    private static final String PATH = "/svc-test";
    private static final String SCHEMA = "{\"root\":{\"id\":\"root\",\"type\":\"LubanContainer\",\"props\":{},\"children\":[]}}";

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM published_pages");
        jdbc.update("DELETE FROM page_versions");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM sites");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SITE_ID, "Svc Test", SLUG, "https://svc.example.com", "active", now, now);
        jdbc.update("INSERT INTO pages (id, site_id, name, path, status, schema_json, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PAGE_ID, SITE_ID, "Svc Page", PATH, "draft", SCHEMA, now, now);
    }

    @Test
    void publish_should_set_status_and_create_published_snapshot() {
        PageResponse result = pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        assertEquals("published", result.status());
        assertNotNull(result.updatedAt());

        // published_pages 表应有记录
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM published_pages WHERE page_id = ?",
                Integer.class, PAGE_ID);
        assertEquals(1, count);
    }

    @Test
    void publish_then_public_service_should_return_content() {
        pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        PageResponse published = publicPageService.getPublishedPageBySlugAndPath(SLUG, PATH);
        assertEquals("published", published.status());
        assertEquals("LubanContainer", published.schema().path("root").path("type").asText());
    }

    @Test
    void editing_draft_after_publish_should_not_change_public_content() {
        // 1. 发布
        pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        // 2. 编辑草稿
        String newSchema = "{\"root\":{\"id\":\"root\",\"type\":\"LubanHero\",\"props\":{},\"children\":[]}}";
        pageService.update(SITE_ID, PAGE_ID, "Svc Page", PATH, "published",
                objectMapperReadTree(newSchema), null);

        // 3. 公开内容应不变
        PageResponse published = publicPageService.getPublishedPageBySlugAndPath(SLUG, PATH);
        assertEquals("LubanContainer", published.schema().path("root").path("type").asText());
    }

    @Test
    void unpublish_should_remove_public_visibility() {
        // 1. 先发布
        pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        // 2. 公开可见
        PageResponse before = publicPageService.getPublishedPageBySlugAndPath(SLUG, PATH);
        assertNotNull(before);

        // 3. 下线
        PageResponse result = pageService.unpublish(SITE_ID, PAGE_ID);
        assertEquals("archived", result.status());

        // 4. 公开接口应抛 PAGE_NOT_FOUND
        assertThrows(BusinessException.class, () ->
                publicPageService.getPublishedPageBySlugAndPath(SLUG, PATH));
    }

    @Test
    void republish_should_update_public_content() {
        // 1. 发布
        pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        // 2. 编辑草稿
        String newSchema = "{\"root\":{\"id\":\"root\",\"type\":\"LubanHero\",\"props\":{\"title\":\"v2\"},\"children\":[]}}";
        pageService.update(SITE_ID, PAGE_ID, "Svc Page", PATH, "published",
                objectMapperReadTree(newSchema), null);

        // 3. 重新发布
        pageService.publish(SITE_ID, PAGE_ID, "admin-1");

        // 4. 公开内容应为新版
        PageResponse published = publicPageService.getPublishedPageBySlugAndPath(SLUG, PATH);
        assertEquals("LubanHero", published.schema().path("root").path("type").asText());
    }

    @Test
    void publish_nonexistent_should_throw() {
        assertThrows(BusinessException.class, () ->
                pageService.publish(SITE_ID, "nonexistent", "admin-1"));
    }

    @Test
    void preview_should_return_draft_content() {
        PageResponse draft = pageService.getPreviewDraft(SITE_ID, PAGE_ID);
        assertEquals("draft", draft.status());
        assertEquals("LubanContainer", draft.schema().path("root").path("type").asText());
    }

    @Test
    void invalid_status_should_throw() {
        assertThrows(BusinessException.class, () ->
                pageService.create(SITE_ID, "Bad", "/bad", "invalid_status",
                        objectMapperReadTree(SCHEMA), null));
    }

    private com.fasterxml.jackson.databind.JsonNode objectMapperReadTree(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
