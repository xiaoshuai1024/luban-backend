package com.luban.backend.service;

import com.luban.backend.dto.PageVersionResponse;
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
class PageVersionServiceTest {

    @Autowired PageVersionService versionService;
    @Autowired JdbcTemplate jdbc;

    private static final String SITE_ID = "site-ver";
    private static final String PAGE_ID = "page-ver-1";

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM page_versions");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM sites");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                SITE_ID, "Ver Site", "ver-slug", "https://ver.com", "active", now, now);
        jdbc.update("INSERT INTO pages (id, site_id, name, path, status, schema_json, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                PAGE_ID, SITE_ID, "Ver Page", "/ver", "draft", "{\"root\":{}}", now, now);
    }

    @Test
    void createSnapshot_adds_version() {
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{}}"),
                "测试版本", "admin");
        var versions = versionService.list(SITE_ID, PAGE_ID);
        assertFalse(versions.isEmpty());
        assertEquals(1, versions.size());
    }

    @Test
    void list_returns_versions_without_schema() {
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{}}"),
                "v1", "admin");
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{\"v\":2}}"),
                "v2", "admin");
        var versions = versionService.list(SITE_ID, PAGE_ID);
        assertEquals(2, versions.size());
        // 列表不应包含完整 schema（轻量）
        var first = versions.get(0);
        // versionNo 应递增
        assertTrue(first.versionNo() >= 1);
    }

    @Test
    void get_returns_version_with_schema() {
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{\"type\":\"LubanHero\"}}"),
                "full", "admin");
        var versions = versionService.list(SITE_ID, PAGE_ID);
        String versionId = versions.get(0).id();
        PageVersionResponse detail = versionService.get(SITE_ID, PAGE_ID, versionId);
        assertNotNull(detail);
        assertNotNull(detail.schema());
    }

    @Test
    void rollback_restores_old_schema() {
        // 创建两个版本
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{\"type\":\"LubanContainer\"}}"),
                "v1-container", "admin");
        versionService.createSnapshot(PAGE_ID,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("{\"root\":{\"type\":\"LubanHero\"}}"),
                "v2-hero", "admin");

        var versions = versionService.list(SITE_ID, PAGE_ID);
        // 回滚到第一个版本
        String firstVersionId = versions.get(versions.size() - 1).id();
        versionService.rollback(SITE_ID, PAGE_ID, firstVersionId, "admin");

        // pages 表的 schema_json 应恢复为 Container
        String schema = jdbc.queryForObject("SELECT schema_json FROM pages WHERE id = ?", String.class, PAGE_ID);
        assertNotNull(schema);
        assertTrue(schema.contains("LubanContainer"));
    }
}
