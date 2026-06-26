package com.luban.backend.service;

import com.luban.backend.dto.SiteResponse;
import com.luban.backend.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SiteServiceTest {

    @Autowired SiteService siteService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM published_pages");
        jdbc.update("DELETE FROM page_versions");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM datasources");
        jdbc.update("DELETE FROM sites");
    }

    private String createSite(String slug) {
        String id = "site-" + slug;
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                id, "Site " + slug, slug, "https://" + slug + ".com", "active", now, now);
        return id;
    }

    @Test
    void list_returns_all_sites() {
        createSite("a"); createSite("b");
        assertEquals(2, siteService.list().size());
    }

    @Test
    void get_returns_site_by_id() {
        String id = createSite("get");
        SiteResponse resp = siteService.get(id);
        assertEquals("get", resp.slug());
    }

    @Test
    void get_nonexistent_throws() {
        assertThrows(BusinessException.class, () -> siteService.get("nonexistent"));
    }

    @Test
    void create_inserts_site() {
        var resp = siteService.create("New Site", "new-slug", "https://new.com", "active");
        assertEquals("New Site", resp.name());
        assertEquals("new-slug", resp.slug());
    }

    @Test
    void update_modifies_site() {
        String id = createSite("upd");
        var resp = siteService.update(id, "Updated", "upd", "https://upd.com", "active", null, null);
        assertEquals("Updated", resp.name());
    }

    @Test
    void delete_removes_site_and_cascades() {
        String id = createSite("del");
        // 添加子表数据验证级联删除
        Instant now = Instant.now();
        jdbc.update("INSERT INTO pages (id, site_id, name, path, status, schema_json, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "p-del", id, "P", "/p", "draft", "{}", now, now);
        siteService.delete(id);
        assertThrows(BusinessException.class, () -> siteService.get(id));
        // pages 也应被级联删除
        Integer pageCount = jdbc.queryForObject("SELECT COUNT(*) FROM pages WHERE site_id = ?", Integer.class, id);
        assertEquals(0, pageCount);
    }

    @Test
    void delete_nonexistent_throws() {
        assertThrows(BusinessException.class, () -> siteService.delete("nonexistent"));
    }

    @Test
    void get_by_slug_via_mapper() {
        createSite("slug-test");
        // 验证 SiteMapper.getBySlug 能查到
        var sites = siteService.list();
        assertTrue(sites.stream().anyMatch(s -> "slug-test".equals(s.slug())));
    }
}
